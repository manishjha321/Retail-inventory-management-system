package com.manish.retailinventory.exception;

import java.util.Map;

public record ApiError(String timestamp, int status, String error, String message, String path,
                       Map<String, String> fieldErrors) {}
