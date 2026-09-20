package com.millerchnz.gpsemulator.gms

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Awaits a Play Services task that carries no result, so location and geofencing calls can be
 * written as straight-line suspending code without pulling in kotlinx-coroutines-play-services.
 */
suspend fun Task<*>.awaitCompletion() {
    if (isComplete) {
        val error = exception
        if (error != null) throw error
        if (isCanceled) throw kotlinx.coroutines.CancellationException("Play Services task cancelled")
        return
    }
    return suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(Unit) }
        addOnFailureListener { continuation.resumeWithException(it) }
        addOnCanceledListener {
            continuation.cancel(kotlinx.coroutines.CancellationException("Play Services task cancelled"))
        }
    }
}
