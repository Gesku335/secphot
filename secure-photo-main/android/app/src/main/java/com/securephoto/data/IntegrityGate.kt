package com.securephoto.data

/**
 * Stage 5 hook. The MVP deliberately does not add Play Services or make a network
 * verdict request. When a Google Cloud project and Play Integrity are available,
 * this gate is the single place to require MEETS_DEVICE_INTEGRITY before open().
 */
interface IntegrityGate { suspend fun allowPhotoOpen(): Boolean }

class DisabledIntegrityGate : IntegrityGate { override suspend fun allowPhotoOpen() = true }
