package ru.aigram.speech.codec

import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_OPUS
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_PCM_S16LE
import org.bytedeco.ffmpeg.global.avutil.AV_LOG_QUIET
import org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_S16
import org.bytedeco.ffmpeg.global.avutil.av_log_set_level
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.javacv.FrameGrabber
import org.springframework.stereotype.Component
import ru.aigram.domain.speech.OutgoingVoiceNote
import ru.aigram.exception.AppException
import ru.aigram.exception.impl.InvalidSpeechAudioException
import ru.aigram.exception.impl.SpeechAudioLimitException
import ru.aigram.exception.impl.SpeechCodecFailedException
import java.nio.ShortBuffer
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.CancellationException
import java.util.stream.Stream

private const val MAX_AUDIO_BYTES = 25L * 1024 * 1024
private const val MAX_AUDIO_SECONDS = 600
private const val RECOGNITION_SAMPLE_RATE = 16000
private const val VOICE_SAMPLE_RATE = 48000
private const val VOICE_BIT_RATE = 32000
private const val AUDIO_CHANNELS = 1
private const val CODEC_THREADS = "2"

@Component
class AudioCodec(private val directory: Path) {
    fun toRecognitionWave(input: Path, output: Path): Path {
        transcode(input, output, true, null)
        return output
    }

    fun toVoiceNote(wav: Path, output: Path): OutgoingVoiceNote {
        val waveform = SpeechWaveform(VOICE_SAMPLE_RATE)
        val samples = transcode(wav, output, false, waveform)
        val duration = ((samples + VOICE_SAMPLE_RATE - 1) / VOICE_SAMPLE_RATE).toInt()
        return OutgoingVoiceNote(output, duration, waveform.packed())
    }

    private fun transcode(input: Path, output: Path, recognition: Boolean, waveform: SpeechWaveform?): Long {
        interrupted()
        val source = input.toAbsolutePath().normalize()
        val target = output.toAbsolutePath().normalize()
        if (source == target || !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS) || Files.size(source) == 0L) {
            throw InvalidSpeechAudioException()
        }
        if (Files.size(source) > MAX_AUDIO_BYTES) {
            throw SpeechAudioLimitException()
        }
        try {
            prepareRuntime()
            Files.createDirectories(target.parent)
            val temporary = Files.createTempFile(target.parent, "speech-", ".part")
            try {
                val count = convert(source, temporary, recognition, waveform)
                interrupted()
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                return count
            } finally {
                Files.deleteIfExists(temporary)
            }
        } catch (exception: AppException) {
            throw exception
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            interrupted()
            throw SpeechCodecFailedException(exception)
        } catch (error: LinkageError) {
            throw SpeechCodecFailedException(error)
        }
    }

    private fun convert(input: Path, output: Path, recognition: Boolean, waveform: SpeechWaveform?): Long {
        val rate = if (recognition) {
            RECOGNITION_SAMPLE_RATE
        } else {
            VOICE_SAMPLE_RATE
        }
        var count = 0L
        FFmpegFrameGrabber(input.toFile()).use { grabber ->
            grabber.format = if (recognition) {
                "ogg"
            } else {
                "wav"
            }
            grabber.audioChannels = AUDIO_CHANNELS
            grabber.sampleRate = rate
            grabber.sampleMode = FrameGrabber.SampleMode.SHORT
            grabber.sampleFormat = AV_SAMPLE_FMT_S16
            grabber.setOption("protocol_whitelist", "file")
            grabber.setOption("analyzeduration", "1000000")
            grabber.setOption("probesize", "1048576")
            grabber.setAudioOption("threads", CODEC_THREADS)
            try {
                grabber.start()
            } catch (exception: FFmpegFrameGrabber.Exception) {
                interrupted()
                throw InvalidSpeechAudioException(exception)
            }
            if (recognition && grabber.audioCodec != AV_CODEC_ID_OPUS) {
                throw InvalidSpeechAudioException()
            }
            if (grabber.lengthInTime > MAX_AUDIO_SECONDS * 1_000_000L) {
                throw SpeechAudioLimitException()
            }
            FFmpegFrameRecorder(output.toFile(), AUDIO_CHANNELS).use { recorder ->
                recorder.format = if (recognition) {
                    "wav"
                } else {
                    "ogg"
                }
                recorder.audioCodec = if (recognition) {
                    AV_CODEC_ID_PCM_S16LE
                } else {
                    AV_CODEC_ID_OPUS
                }
                if (!recognition) {
                    recorder.audioCodecName = "libopus"
                    recorder.audioBitrate = VOICE_BIT_RATE
                    recorder.setAudioOption("application", "voip")
                }
                recorder.audioChannels = AUDIO_CHANNELS
                recorder.sampleRate = rate
                recorder.setAudioOption("threads", CODEC_THREADS)
                recorder.start()
                Stream.generate {
                    interrupted()
                    grabber.grabSamples()
                }.takeWhile { it != null }.forEach { frame ->
                    if (frame != null) {
                        interrupted()
                        val buffers = frame.samples
                        val samples = buffers?.firstOrNull() as? ShortBuffer
                        if (buffers == null || buffers.size != AUDIO_CHANNELS || samples == null) {
                            throw InvalidSpeechAudioException()
                        }
                        count += samples.remaining()
                        if (count > MAX_AUDIO_SECONDS.toLong() * rate) {
                            throw SpeechAudioLimitException()
                        }
                        waveform?.observe(samples)
                        recorder.recordSamples(frame.sampleRate, frame.audioChannels, *buffers)
                    }
                }
            }
        }
        if (count == 0L) {
            throw InvalidSpeechAudioException()
        }
        return count
    }

    private fun prepareRuntime() {
        if (System.getProperty("org.bytedeco.javacpp.cachedir") == null) {
            val cache = directory.resolve("speech/runtime/ffmpeg").toAbsolutePath()
            Files.createDirectories(cache)
            System.setProperty("org.bytedeco.javacpp.cachedir", cache.toString())
        }
        av_log_set_level(AV_LOG_QUIET)
    }

    private fun interrupted() {
        if (Thread.currentThread().isInterrupted) {
            throw CancellationException()
        }
    }
}
