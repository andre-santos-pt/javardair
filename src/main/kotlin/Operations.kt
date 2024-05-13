enum class Operations {
    FETCH, // requests the file
    PULL, // requests the transformations
    PUSH, // sends the transformations
    NOTIFY_CONFLICTS,
    REQUEST_ROOT_FILE,
}