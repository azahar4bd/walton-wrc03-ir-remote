package com.azahar4bd.walton.wrc03.ir

import com.azahar4bd.walton.wrc03.model.RemoteCommand
import com.azahar4bd.walton.wrc03.model.RemoteProfile

/** Why a button press did not reach the TV, in Bengali, ready to show to the user. */
enum class SendFailure(
    val messageBn: String,
) {
    NO_IR_HARDWARE(
        "এই ফোনে IR blaster পাওয়া যায়নি, তাই সিগন্যাল পাঠানো সম্ভব নয়।",
    ),
    CARRIER_UNSUPPORTED(
        "ফোনে IR আছে, কিন্তু সেটি 38 kHz ক্যারিয়ার সমর্থন করে না — NEC (6122) সিগন্যাল পাঠানো যাবে না।",
    ),
    UNKNOWN_COMMAND(
        "এই বাটনের জন্য বর্তমান প্রোফাইলে কোনো কোড নেই।",
    ),
    HARDWARE_ERROR(
        "IR সিগন্যাল পাঠানো যায়নি — ফোনের IR ড্রাইভার এরর দিয়েছে।",
    ),
}

/** Outcome of one press, either the pattern we beamed out or the reason we could not. */
sealed interface IrSendResult {
    val carrierHz: Int?
    val pattern: IntArray?

    data class Sent(
        val command: RemoteCommand,
        val code: Int,
        override val carrierHz: Int,
        override val pattern: IntArray,
        val isRepeat: Boolean = false,
    ) : IrSendResult

    data class Failed(
        val reason: SendFailure,
    ) : IrSendResult {
        override val carrierHz: Int? get() = null
        override val pattern: IntArray? get() = null
    }
}

/**
 * Sends NEC frames through an [IrHardware].
 *
 * Holds no Android types on purpose: unit tests drive it with a fake emitter.
 */
class IrRemoteController(
    private val hardware: IrHardware?,
) {
    fun availability(targetFrequencyHz: Int = NecProtocol.CARRIER_FREQUENCY_HZ): IrAvailability =
        IrAvailabilityDetector.detect(hardware, targetFrequencyHz)

    /** Transmit the full 32 bit frame for [command] using [profile]'s codes. */
    fun send(
        command: RemoteCommand,
        profile: RemoteProfile,
    ): IrSendResult {
        val code = profile.codeFor(command) ?: return IrSendResult.Failed(SendFailure.UNKNOWN_COMMAND)
        return send(command, NecFrame(profile.address, code.command, profile.addressMode), profile.carrierHz)
    }

    /**
     * Transmit an explicit frame for [command], ignoring any code table.
     *
     * This is what the code scanner uses: it sweeps candidate frames until the user says
     * the TV reacted, so the value on the wire is not (yet) in [RemoteProfile.commands].
     */
    fun send(
        command: RemoteCommand,
        frame: NecFrame,
        carrierHz: Int = NecProtocol.CARRIER_FREQUENCY_HZ,
    ): IrSendResult =
        transmit(
            carrierHz = carrierHz,
            pattern = NecEncoder.encodeFrame(frame),
            command = command,
            code = frame.command,
            isRepeat = false,
        )

    /**
     * NEC repeat frame (9 ms / 2.25 ms / 562.5 us), sent every
     * [NecProtocol.REPEAT_PERIOD_US] by real handsets while a key is held.
     */
    fun sendRepeat(
        command: RemoteCommand,
        profile: RemoteProfile,
    ): IrSendResult {
        val code = profile.codeFor(command) ?: return IrSendResult.Failed(SendFailure.UNKNOWN_COMMAND)
        return transmit(
            carrierHz = profile.carrierHz,
            pattern = NecEncoder.encodeRepeatFrame(),
            command = command,
            code = code.command,
            isRepeat = true,
        )
    }

    private fun transmit(
        carrierHz: Int,
        pattern: IntArray,
        command: RemoteCommand,
        code: Int,
        isRepeat: Boolean,
    ): IrSendResult {
        when (availability(carrierHz)) {
            IrAvailability.NotSupported, IrAvailability.NoEmitter ->
                return IrSendResult.Failed(SendFailure.NO_IR_HARDWARE)

            is IrAvailability.CarrierUnsupported ->
                return IrSendResult.Failed(SendFailure.CARRIER_UNSUPPORTED)

            is IrAvailability.Ready -> Unit
        }

        val emitter = hardware ?: return IrSendResult.Failed(SendFailure.NO_IR_HARDWARE)
        return try {
            emitter.transmit(carrierHz, pattern)
            IrSendResult.Sent(command, code, carrierHz, pattern, isRepeat)
        } catch (error: RuntimeException) {
            // Some OEM drivers throw IllegalArgumentException / SecurityException here.
            IrSendResult.Failed(SendFailure.HARDWARE_ERROR)
        }
    }
}
