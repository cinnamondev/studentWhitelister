package com.github.cinnamondev.studentWhitelister;

public final class Exceptions {
    public static class CancelledDialogException extends RuntimeException {
        public CancelledDialogException() { super(); }
    }
    public static class DiscordValidationException extends RuntimeException {
        public DiscordValidationException() { super(); }
        public DiscordValidationException(Throwable ex) { super(ex); }
        public DiscordValidationException(String message) { super(message); }

    }
    public static class IdentifierValidationException extends RuntimeException {
        public IdentifierValidationException() { super(); }
        public IdentifierValidationException(Throwable ex) { super(ex); }
        public IdentifierValidationException(String message) { super(message); }
    }
    public static class UnreachableUserException extends RuntimeException {
        public UnreachableUserException() { super(); }
        public UnreachableUserException(Throwable ex) { super(ex); }
        public UnreachableUserException(String message) { super(message); }
    }
    public static class InvalidMinecraftUser extends RuntimeException {
        public InvalidMinecraftUser() { super(); }
        public InvalidMinecraftUser(Throwable ex) { super(ex); }
        public InvalidMinecraftUser(String message) { super(message); }
    }
}
