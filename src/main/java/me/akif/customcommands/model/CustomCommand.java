package me.akif.customcommands.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory representation of a single custom command stored in
 * config.yml. Mutations made through the admin command go through
 * {@link me.akif.customcommands.manager.ConfigManager} so the file
 * stays in sync with the runtime state.
 */
public class CustomCommand {

    private final String name;
    private boolean enabled;
    private String permission;
    private String permissionMessageKey;
    private int usageLimitTotal;
    private int usageLimitPerPlayer;
    private long cooldownSeconds;
    private long activeTimeSeconds;
    private long createdAt;
    private List<String> consoleCommands;
    private List<String> broadcastMessages;
    private final Map<String, List<String>> messageOverrides;

    public CustomCommand(String name) {
        this.name = name.toLowerCase();
        this.enabled = true;
        this.permission = "";
        this.permissionMessageKey = "command.no-permission";
        this.usageLimitTotal = -1;
        this.usageLimitPerPlayer = -1;
        this.cooldownSeconds = 0L;
        this.activeTimeSeconds = 0L;
        this.createdAt = System.currentTimeMillis() / 1000L;
        this.consoleCommands = new ArrayList<>();
        this.broadcastMessages = new ArrayList<>();
        this.messageOverrides = new HashMap<>();
    }

    public String getName() {
        return name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPermission() {
        return permission == null ? "" : permission;
    }

    public void setPermission(String permission) {
        this.permission = permission == null ? "" : permission;
    }

    public String getPermissionMessageKey() {
        return permissionMessageKey;
    }

    public void setPermissionMessageKey(String permissionMessageKey) {
        this.permissionMessageKey = permissionMessageKey == null
                ? "command.no-permission"
                : permissionMessageKey;
    }

    public int getUsageLimitTotal() {
        return usageLimitTotal;
    }

    public void setUsageLimitTotal(int usageLimitTotal) {
        this.usageLimitTotal = usageLimitTotal;
    }

    public int getUsageLimitPerPlayer() {
        return usageLimitPerPlayer;
    }

    public void setUsageLimitPerPlayer(int usageLimitPerPlayer) {
        this.usageLimitPerPlayer = usageLimitPerPlayer;
    }

    public long getCooldownSeconds() {
        return cooldownSeconds;
    }

    public void setCooldownSeconds(long cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
    }

    public long getActiveTimeSeconds() {
        return activeTimeSeconds;
    }

    public void setActiveTimeSeconds(long activeTimeSeconds) {
        this.activeTimeSeconds = activeTimeSeconds;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public List<String> getConsoleCommands() {
        return consoleCommands;
    }

    public void setConsoleCommands(List<String> consoleCommands) {
        this.consoleCommands = consoleCommands == null ? new ArrayList<>() : consoleCommands;
    }

    public void addConsoleCommand(String cmd) {
        this.consoleCommands.add(cmd);
    }

    public boolean removeConsoleCommand(int index) {
        if (index < 0 || index >= consoleCommands.size()) {
            return false;
        }
        consoleCommands.remove(index);
        return true;
    }

    public List<String> getBroadcastMessages() {
        return broadcastMessages;
    }

    public void setBroadcastMessages(List<String> broadcastMessages) {
        this.broadcastMessages = broadcastMessages == null ? new ArrayList<>() : broadcastMessages;
    }

    public void addBroadcastMessage(String line) {
        this.broadcastMessages.add(line);
    }

    public boolean removeBroadcastMessage(int index) {
        if (index < 0 || index >= broadcastMessages.size()) {
            return false;
        }
        broadcastMessages.remove(index);
        return true;
    }

    public void clearBroadcastMessages() {
        broadcastMessages.clear();
    }

    public Map<String, List<String>> getMessageOverrides() {
        return messageOverrides;
    }

    public List<String> getMessageOverride(String key) {
        return messageOverrides.get(key);
    }

    public void setMessageOverride(String key, List<String> value) {
        if (value == null) {
            messageOverrides.remove(key);
        } else {
            messageOverrides.put(key, value);
        }
    }

    /**
     * Returns true once the command has lived past its
     * {@link #activeTimeSeconds} window. A value of zero means
     * the command never expires.
     */
    public boolean isExpired() {
        if (activeTimeSeconds <= 0L) return false;
        if (createdAt <= 0L) return false;
        long now = System.currentTimeMillis() / 1000L;
        return now > (createdAt + activeTimeSeconds);
    }
}
