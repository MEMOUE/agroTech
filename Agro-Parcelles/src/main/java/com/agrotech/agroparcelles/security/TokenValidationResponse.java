package com.agrotech.agroparcelles.security;

public record TokenValidationResponse(boolean valid, String username, String role) {
}