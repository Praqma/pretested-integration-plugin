package org.jenkinsci.plugins.pretestedintegration.scm.git;

public class GitUtils {
    public static String removeRemoteFromBranchName(String branchName) {
        if (branchName == null) throw new IllegalArgumentException("String branchName parameter is null");
        if (branchName.isEmpty()) throw new IllegalArgumentException("String branchName parameter is empty");

        String[] split = branchName.split("/");

        String branchNameWithNoRemote = "";
        for (int i = 1; i < split.length; i++) {
            branchNameWithNoRemote += split[i] + "/";
        }
        branchNameWithNoRemote = branchNameWithNoRemote.substring(0, branchNameWithNoRemote.length() - 1);

        return branchNameWithNoRemote;
    }
}
