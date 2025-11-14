package com.paybridge.Exceptions;

public class EmailNotVerifiedException extends RuntimeException{

    public EmailNotVerifiedException(String message){
        super(message);
    }
}
