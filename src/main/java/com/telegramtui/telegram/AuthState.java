package com.telegramtui.telegram;

public interface AuthState {
	record WaitPhone() implements AuthState {}

	record WaitCode() implements AuthState {}

	record WaitPassword(String hint, String recoveryEmailPattern) implements AuthState {}

	record Ready() implements AuthState {}

	record Closed() implements AuthState {}
}
