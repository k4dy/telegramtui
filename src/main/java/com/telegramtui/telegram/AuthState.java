package com.telegramtui.telegram;

public sealed interface AuthState {
	record WaitPhone() implements AuthState {}

	record WaitCode() implements AuthState {}

	record WaitPassword(String hint, String recoveryEmailPattern) implements AuthState {}

	record Ready() implements AuthState {}

	record Closed() implements AuthState {}
}
