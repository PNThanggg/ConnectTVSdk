package com.connect.service.capability.listeners;

import com.connect.service.command.ServiceCommandError;

public interface ErrorListener {

    /**
     * Method to return the error that was generated. Will pass an error object with a helpful status code and error message.
     * 
     * @param error ServiceCommandError describing the error
     */
    void onError(ServiceCommandError error);
}
