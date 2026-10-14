package com.azahar4bd.walton.wrc03

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.azahar4bd.walton.wrc03.data.RemoteProfiles
import com.azahar4bd.walton.wrc03.data.ScannedCodeStore
import com.azahar4bd.walton.wrc03.databinding.ActivityCodeScanBinding
import com.azahar4bd.walton.wrc03.ir.ConsumerIrHardware
import com.azahar4bd.walton.wrc03.ir.IrRemoteController
import com.azahar4bd.walton.wrc03.model.RemoteCommand
import com.azahar4bd.walton.wrc03.model.RemoteProfile
import com.azahar4bd.walton.wrc03.scan.CodeScanEngine
import com.azahar4bd.walton.wrc03.scan.HandlerScanScheduler
import com.azahar4bd.walton.wrc03.scan.ScanPreset
import com.azahar4bd.walton.wrc03.scan.ScanSnapshot
import com.azahar4bd.walton.wrc03.scan.ScanState
import com.azahar4bd.walton.wrc03.scan.ScanTransmitter
import com.azahar4bd.walton.wrc03.ui.RemoteText
import com.google.android.material.snackbar.Snackbar
import kotlin.math.roundToInt

/**
 * Code scan mode: sweep candidate NEC frames at the TV until one of them makes it react.
 *
 * The shipped code table is unverified, so this screen is how a user turns the remote into
 * something that actually works: pick a button, pick how wide to sweep, watch the set and
 * tap "টিভি সাড়া দিয়েছে!" the moment it responds. The winning frame is written to
 * [ScannedCodeStore] and overrides the shipped value for that button.
 *
 * All the sweep logic lives in [CodeScanEngine] (pure Kotlin, unit tested); this class is
 * only views, a Handler and the IR hardware.
 */
class CodeScanActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCodeScanBinding
    private lateinit var controller: IrRemoteController
    private lateinit var store: ScannedCodeStore

    private val scheduler = HandlerScanScheduler()

    private var profile: RemoteProfile = RemoteProfiles.DEFAULT
    private var target: RemoteCommand = RemoteCommand.POWER
    private var preset: ScanPreset = ScanPreset.QUICK
    private var intervalMs: Long = CodeScanEngine.DEFAULT_INTERVAL_MS
    private var engine: CodeScanEngine? = null
    private var defaultStateColour: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCodeScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        controller = IrRemoteController(ConsumerIrHardware.create(this))
        store = ScannedCodeStore(this)
        defaultStateColour = binding.scanStateText.currentTextColor

        profile = intentProfile()
        target = intentTarget()

        binding.buttonClose.setOnClickListener { finish() }
        binding.buttonStartPause.setOnClickListener { toggleRun() }
        binding.buttonStep.setOnClickListener { engine?.skip() }
        binding.buttonReset.setOnClickListener { engine?.stop() }
        binding.buttonFound.setOnClickListener { markFound() }
        binding.buttonSaveClose.setOnClickListener { saveAndClose() }

        setupCommandSpinner()
        setupPresetSpinner()
        setupIntervalSeek()
        rebuildEngine()
    }

    /** Never keep beaming codes at the TV once the screen is not in front of the user. */
    override fun onPause() {
        engine?.pause()
        scheduler.cancel()
        super.onPause()
    }

    // --- set up --------------------------------------------------------------

    private fun intentProfile(): RemoteProfile {
        val id = intent.getStringExtra(EXTRA_PROFILE_ID) ?: return RemoteProfiles.DEFAULT
        return RemoteProfiles.ALL.firstOrNull { it.id == id } ?: RemoteProfiles.DEFAULT
    }

    private fun intentTarget(): RemoteCommand {
        val name = intent.getStringExtra(EXTRA_COMMAND) ?: return RemoteCommand.POWER
        return RemoteCommand.entries.firstOrNull { it.name == name } ?: RemoteCommand.POWER
    }

    private fun setupCommandSpinner() {
        val labels = RemoteCommand.entries.map { it.labelBn }
        binding.scanCommandSpinner.adapter = spinnerAdapter(labels)
        binding.scanCommandSpinner.setSelection(RemoteCommand.entries.indexOf(target))
        binding.scanCommandSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    target = RemoteCommand.entries[position]
                    rebuildEngine()
                }

                override fun onNothingSelected(parent: AdapterView<*>) = Unit
            }
    }

    private fun setupPresetSpinner() {
        val labels = ScanPreset.entries.map { RemoteText.scanPresetLabel(it) }
        binding.scanPresetSpinner.adapter = spinnerAdapter(labels)
        binding.scanPresetSpinner.setSelection(ScanPreset.entries.indexOf(preset))
        binding.scanPresetSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    preset = ScanPreset.entries[position]
                    rebuildEngine()
                }

                override fun onNothingSelected(parent: AdapterView<*>) = Unit
            }
    }

    private fun spinnerAdapter(labels: List<String>): ArrayAdapter<String> =
        ArrayAdapter(this, android.R.layout.simple_spinner_item, labels).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

    private fun setupIntervalSeek() {
        binding.scanIntervalSeek.max = INTERVAL_STEPS
        binding.scanIntervalSeek.progress = stepsFor(CodeScanEngine.DEFAULT_INTERVAL_MS)
        binding.scanIntervalSeek.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean,
                ) {
                    intervalMs = CodeScanEngine.MIN_INTERVAL_MS + (progress.toLong() * INTERVAL_STEP_MS)
                    updateIntervalLabel()
                    updatePlanSummary()
                    // Retuning live keeps a running sweep going instead of restarting it.
                    engine?.intervalMs = intervalMs
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            },
        )
        updateIntervalLabel()
    }

    private fun updateIntervalLabel() {
        binding.scanIntervalLabel.text =
            "${getString(R.string.scan_interval_label)}: ${RemoteText.secondsBn(intervalMs)} সেকেন্ড"
    }

    private fun updatePlanSummary() {
        val plan = engine?.plan ?: preset.planFor(target, profile)
        binding.scanPlanSummary.text = RemoteText.scanPlanSummary(plan, intervalMs)
    }

    // --- sweeping ------------------------------------------------------------

    /** New button or new sweep width => a fresh plan, and the sweep starts over. */
    private fun rebuildEngine() {
        engine?.stop()
        scheduler.cancel()

        val plan = preset.planFor(target, profile)
        val transmitter =
            ScanTransmitter { candidate ->
                controller.send(target, candidate.frame(), candidate.carrierHz)
            }
        val scan =
            CodeScanEngine(
                plan = plan,
                transmitter = transmitter,
                scheduler = scheduler,
                initialIntervalMs = intervalMs,
            )
        scan.listener = { snapshot -> render(snapshot) }
        engine = scan

        updatePlanSummary()
        render(scan.snapshot())
    }

    private fun toggleRun() {
        val scan = engine ?: return
        when (scan.state) {
            ScanState.RUNNING -> scan.pause()
            ScanState.PAUSED -> scan.resume()
            ScanState.IDLE, ScanState.FINISHED, ScanState.FAILED -> scan.start()
        }
        binding.root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun markFound() {
        val scan = engine ?: return
        val alreadyFound = scan.found.size
        scan.markFound(System.currentTimeMillis())
        binding.root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

        val message =
            if (scan.found.size > alreadyFound) R.string.scan_found_hint else R.string.scan_found_nothing
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun saveAndClose() {
        val records = engine?.found.orEmpty()
        if (records.isNotEmpty()) {
            store.saveAll(records)
            setResult(RESULT_OK, Intent().putExtra(EXTRA_SAVED_COUNT, records.size))
        }
        finish()
    }

    // --- rendering -----------------------------------------------------------

    private fun render(snapshot: ScanSnapshot) {
        binding.scanCurrentCode.text = RemoteText.scanCandidateLine(snapshot.candidate)
        binding.scanStateText.text = RemoteText.scanStateLine(snapshot)
        binding.scanProgressText.text = RemoteText.scanProgress(snapshot)
        binding.scanProgress.progress = (snapshot.progress * PROGRESS_MAX).roundToInt()
        binding.scanFoundList.text = RemoteText.scanFoundSheet(snapshot.found)

        binding.buttonStartPause.text = RemoteText.scanStartPauseLabel(snapshot.state)
        binding.buttonFound.isEnabled = snapshot.canMarkFound
        binding.buttonStep.isEnabled = snapshot.isRunning
        binding.buttonSaveClose.isEnabled = snapshot.found.isNotEmpty()

        val failed = snapshot.state == ScanState.FAILED
        binding.scanStateText.setTextColor(
            if (failed) ContextCompat.getColor(this, R.color.status_error) else defaultStateColour,
        )

        // A long sweep should not let the screen go to sleep mid-way.
        binding.root.keepScreenOn = snapshot.isRunning
    }

    companion object {
        private const val EXTRA_PROFILE_ID = "com.azahar4bd.walton.wrc03.extra.PROFILE_ID"
        private const val EXTRA_COMMAND = "com.azahar4bd.walton.wrc03.extra.COMMAND"

        /** Number of codes written to [ScannedCodeStore], returned with `RESULT_OK`. */
        const val EXTRA_SAVED_COUNT = "com.azahar4bd.walton.wrc03.extra.SAVED_COUNT"

        private const val INTERVAL_STEP_MS = 100L
        private const val PROGRESS_MAX = 100

        private val INTERVAL_STEPS =
            ((CodeScanEngine.MAX_INTERVAL_MS - CodeScanEngine.MIN_INTERVAL_MS) / INTERVAL_STEP_MS).toInt()

        /**
         * @param command the button to open the scanner on, e.g. after a long press on the
         *                keypad; defaults to POWER.
         */
        fun intent(
            context: Context,
            profileId: String,
            command: RemoteCommand? = null,
        ): Intent =
            Intent(context, CodeScanActivity::class.java)
                .putExtra(EXTRA_PROFILE_ID, profileId)
                .apply { command?.let { putExtra(EXTRA_COMMAND, it.name) } }

        private fun stepsFor(intervalMs: Long): Int =
            ((intervalMs - CodeScanEngine.MIN_INTERVAL_MS) / INTERVAL_STEP_MS).toInt()
    }
}
