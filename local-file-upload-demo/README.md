# Local File Upload Demo

A Spring Boot web application that accepts browser uploads and saves them on the machine running the application.

## Open in IntelliJ

1. Choose **File > Open**.
2. Select the `local-file-upload-demo` directory.
3. Let IntelliJ import `pom.xml` as a Maven project.
4. Use Java 17 or newer.
5. Run `FileUploadApplication`.
6. Open `http://localhost:8081`.

By default, files are saved in the `uploads` directory under the application's working directory. To use another absolute directory, add this IntelliJ environment variable:

```text
FILE_STORAGE_LOCATION=C:\Users\Lenovo\Documents\MyUploads
```

The maximum file size is 25 MB. Change `spring.servlet.multipart.max-file-size` and `max-request-size` in `application.yml` if needed.

## Security notes

- Stored filenames receive a UUID to prevent overwriting files with the same original name.
- Paths are normalized and constrained to the configured upload directory.
- Downloads use attachment disposition rather than displaying uploaded content inline.
- This learning project has no authentication. Do not expose it to a public network until authentication, authorization, malware scanning, file-type policy, quotas, audit logging, and rate limiting are added.
