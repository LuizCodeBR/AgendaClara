package br.com.luizcode.agenda.catalog;

import java.time.LocalTime;

public record WorkingWindow(LocalTime opensAt, LocalTime closesAt) {}
