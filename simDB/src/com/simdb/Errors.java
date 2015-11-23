package com.simdb;

/*
        Success code:
                0 OK

        Error codes:
                1 Wrong authentication
                2 Incomplete set
                3 No such specifier
                4 Too much data
                5 No such set
                6 Wrong type
                7 Unknown name
                8 Unknown field

               99 Generic error
 */

public class Errors {
	public static class WrongAuthentication extends Exception {
	}
	public static class IncompleteSet extends Exception {
	}
	public static class NoSuchSpecifier extends Exception {
	}
	public static class TooMuchData extends Exception {
	}
	public static class NoSuchSet extends Exception {
	}
	public static class WrongType extends Exception {
	}
	public static class UnknownName extends Exception {
	}
	public static class UnknownField extends Exception {
	}
	public static class GenericError extends Exception {
	}
}
