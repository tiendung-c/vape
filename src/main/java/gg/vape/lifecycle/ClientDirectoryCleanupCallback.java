package gg.vape.lifecycle;

public class ClientDirectoryCleanupCallback
implements ClientLifecycleCallback {
    @Override
    public void log(String message) {
    }

    public ClientDirectoryCleanupCallback() {
        // Settings and cached textures are persistent in the consolidated client.
    }


    @Override
    public void close() {
    }
}
