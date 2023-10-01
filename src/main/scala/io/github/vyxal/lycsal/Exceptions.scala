package io.github.vyxal.lycsal

class ValidationException(message: String) extends RuntimeException(s"Module validation failed! This is probably a bug in the compiler.\n$message")
class SubprocessExitExeption(command: String, code: Int) extends RuntimeException(s"Subprocess $command exited with non-zero status code $code!")