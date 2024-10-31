package tukano.impl.storage;

import static tukano.api.Result.error;
import static tukano.api.Result.ok;
import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.Result.ErrorCode.CONFLICT;
import static tukano.api.Result.ErrorCode.INTERNAL_ERROR;
import static tukano.api.Result.ErrorCode.NOT_FOUND;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Consumer;

import tukano.api.Result;
import utils.Hash;
import utils.IO;

public class CloudsystemStorage implements BlobStorage {
    private final String rootDir;
    private static final int CHUNK_SIZE = 4096;
    private static final String DEFAULT_ROOT_DIR = "/tmp/";

    private static final String CONTAINER_NAME = "";

    public CloudsystemStorage() {
        this.rootDir = DEFAULT_ROOT_DIR;
    }

    private static final String storageConnectionString = "";
    private BlobContainerClient containerClient = new BlobContainerClientBuilder()
            .connectionString(storageConnectionString)
            .containerName(CONTAINER_NAME)
            .buildClient();

    @Override
    public Result<Void> write(String path, byte[] bytes) {
        if (path == null)
            return error(BAD_REQUEST);

        var blobClient = containerClient.getBlobClient(path);
        // var file = toFile(path);
        // var pathData = path.split("/");
        // file.getName(); // perguntar ao silveira sobre file
        if (blobClient.exists()) { // hash calculado disto == hash do blob na cloud
            if (Arrays.equals(Hash.sha256(bytes), Hash.sha256(blobClient.downloadContent().toBytes())))
                return ok();
            else
                return error(CONFLICT);

        }
        // IO.write(file, bytes);
        // blob upload from bytes
        var uploadData = BinaryData.fromBytes(bytes);
        blobClient.upload(uploadData);

        return ok();
    }

    @Override
    public Result<byte[]> read(String path) {
        if (path == null)
            return error(BAD_REQUEST);

        var blobClient = containerClient.getBlobClient(path);
        // var file = toFile(path);
        // var pathData = path.split("/");
        // file.getName(); // perguntar ao silveira sobre file
        if (!blobClient.exists()) { // hash calculado disto == hash do blob na cloud
            return error(NOT_FOUND);
        }
        var bytes = blobClient.downloadContent().toBytes();
        return bytes != null ? ok(bytes) : error(INTERNAL_ERROR);
    }

    // sink = buffer, download blob pouco a pouco
    @Override
    public Result<Void> read(String path, Consumer<byte[]> sink) {
        if (path == null)
            return error(BAD_REQUEST);

        var blobClient = containerClient.getBlobClient(path);
        if (!blobClient.exists()) {
            return error(NOT_FOUND);
        }

        var outputStream = new ByteArrayOutputStream();

        blobClient.downloadStream(outputStream);
        sink.accept(outputStream.toByteArray());
        return ok();
    }

    @Override
    public Result<Void> delete(String path) {
        if (path == null)
            return error(BAD_REQUEST);

        try {

            var blobClient = containerClient.getBlobClient(path);
            // blobClient.deleteIfExists();
            if (!blobClient.exists()) {
                return error(NOT_FOUND);
            }

            blobClient.delete();

        } catch (Exception e) {
            e.printStackTrace();
            return error(INTERNAL_ERROR);
        }
        return ok();
    }

}
