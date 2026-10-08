package com.apkorganizer.data

/** Typed error thrown by [ApkManager] operations. */
class ApkManagerException(message: String) : Exception(message)

/** Thrown when a move operation cannot resolve a conflict. Kept for parity. */
class MoveConflictException(message: String) : Exception(message)
