package mobility.model

class ServerException(
    val type: String,
    message: String,
) : Throwable(message)