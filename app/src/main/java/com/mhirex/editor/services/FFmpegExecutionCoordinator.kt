package com.mhirex.editor.services

import java.util.concurrent.Semaphore

/** FFmpegKit shares native state across commands; keep sessions in this app strictly serialized. */
object FFmpegExecutionCoordinator {
    val semaphore = Semaphore(1, true)
}
