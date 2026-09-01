package com.poc.bundle;

import com.poc.repository.AppUser;
import com.poc.repository.Delegation;
import com.poc.repository.Permission;
import com.poc.repository.Role;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class BundleService {

    @Inject
    ObjectMapper objectMapper;

    /** Legge lo stato da Oracle e costruisce il JSON dei dati per OPA. */
    @Transactional
    public String buildDataJson() {
        // user_roles: { "mario": ["admin"], ... }
        Map<String, List<String>> userRoles = new LinkedHashMap<>();
        for (AppUser u : AppUser.<AppUser>listAll()) {
            List<String> roleNames = new ArrayList<>();
            for (Role r : u.roles) {
                roleNames.add(r.name);
            }
            userRoles.put(u.username, roleNames);
        }

        // role_permissions: { "admin": [ {action, resource}, ... ], ... }
        Map<String, List<Map<String, String>>> rolePerms = new LinkedHashMap<>();
        for (Role r : Role.<Role>listAll()) {
            List<Map<String, String>> perms = new ArrayList<>();
            for (Permission p : r.permissions) {
                Map<String, String> perm = new LinkedHashMap<>();
                perm.put("action", p.action);
                perm.put("resource", p.resource);
                perms.add(perm);
            }
            rolePerms.put(r.name, perms);
        }

        // delegations: [ {from_user, to_user, action, resource}, ... ]
        List<Map<String, String>> delegations = new ArrayList<>();
        for (Delegation d : Delegation.<Delegation>listAll()) {
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("from_user", d.fromUser);
            entry.put("to_user", d.toUser);
            entry.put("action", d.action);
            entry.put("resource", d.resource);
            delegations.add(entry);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user_roles", userRoles);
        data.put("role_permissions", rolePerms);
        data.put("delegations", delegations);

        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new RuntimeException("Errore serializzazione data.json", e);
        }
    }

    /** SHA-256 del contenuto dati -> revision del bundle e ETag. */
    public String revision(String dataJson) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(dataJson.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Impacchetta manifest + data in un bundle .tar.gz (solo dati, niente policy). */
    public byte[] buildBundle(String dataJson, String revision) {
        String manifest = "{\"revision\":\"" + revision
                + "\",\"roots\":[\"user_roles\",\"role_permissions\",\"delegations\"]}";
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gz = new GZIPOutputStream(baos);
                 TarArchiveOutputStream tar = new TarArchiveOutputStream(gz)) {
                addEntry(tar, ".manifest", manifest);
                addEntry(tar, "data.json", dataJson);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Errore costruzione bundle", e);
        }
    }

    private void addEntry(TarArchiveOutputStream tar, String name, String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        TarArchiveEntry entry = new TarArchiveEntry(name);
        entry.setSize(bytes.length);
        tar.putArchiveEntry(entry);
        tar.write(bytes);
        tar.closeArchiveEntry();
    }
}