package com.example.fileupload.web;

import com.example.fileupload.storage.FileStorageException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
public class UploadExceptionHandler {
    @ExceptionHandler(FileStorageException.class)
    String storageError(FileStorageException exception, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error", exception.getMessage());
        return "redirect:/";
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    String tooLarge(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error", "The file exceeds the 25 MB limit.");
        return "redirect:/";
    }
}
