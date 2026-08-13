package com.example.googlesso.web;

import com.example.googlesso.files.FileStorageException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
public class FileUploadExceptionHandler {
    @ExceptionHandler(FileStorageException.class)
    String storageError(FileStorageException exception, RedirectAttributes attributes) {
        attributes.addFlashAttribute("error", exception.getMessage());
        return "redirect:/files";
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    String tooLarge(RedirectAttributes attributes) {
        attributes.addFlashAttribute("error", "The file exceeds the 25 MB limit.");
        return "redirect:/files";
    }
}
