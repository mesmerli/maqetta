package org.davinci.server.internal.command;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.davinci.server.Command;
import org.davinci.server.user.User;
import org.davinci.server.util.JSONReader;

public class ModifyLib extends Command {

    @Override
    public void handleCommand(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {

        String libJson = req.getParameter("libChanges");
        List list = (List) JSONReader.read(libJson);
        for (int i = 0; i < list.size(); i++) {
            HashMap libEntry = (HashMap) list.get(i);
            String id = (String) libEntry.get("id");
            String version = (String) libEntry.get("version");
            Boolean installed = (Boolean) libEntry.get("installed");
            String path = (String) libEntry.get("path");
            String base = (String) libEntry.get("base");
            if (installed != null) {
                // add or remove the library
                this.updateLib(user, id, version, installed, base);// Boolean.parseBoolean(installedString));
            }
            if (path != null) {
                // update library root
                this.changeLibraryRoot(user, id, version, path, base);
            }
        }

        responseString = "OK";
    }

    public void changeLibraryRoot(User user, String id, String version, String path, String base) {
        user.modifyLibrary(id, version, path, base);
    }

    public void updateLib(User user, String id, String version, boolean installed, String base) {
        user.modifyLibrary(id, version, base, installed);
    }

}
