package org.jenkinsci.plugins.pretestedintegration.scm.git;

import hudson.model.AbstractBuild;
import hudson.plugins.git.Branch;
import hudson.plugins.git.util.BuildData;
import org.eclipse.jgit.lib.ObjectId;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationBuildWrapper;
import org.jenkinsci.plugins.pretestedintegration.exceptions.*;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PretestedIntegrationGitUtils {

    private static final Logger LOGGER = Logger.getLogger(PretestedIntegrationGitUtils.class.getName());

    /**
     * Counts the commits in the relevant BuildData
     * @param commitId The BuildData from the build
     * @param client  The GitClient
     * @param devBranch the development branch we want to count commits one
     * @return the amount of commits
     * @throws IOException
     * @throws InterruptedException
     */
    public static int countCommits(ObjectId commitId, GitClient client, String devBranch ) throws IOException, InterruptedException {
        GetCommitCountFromBranchCallback commitCountCallback = new GetCommitCountFromBranchCallback(commitId, devBranch);
        int commitCount = client.withRepository(commitCountCallback);
        return commitCount;
    }

    /**
     * Retrieves the BuildData for the given build relevant to the Integration Repository.
     * <ul>
     *  <li>
     *      Extracts only BuildData belonging to the Integration repository
     *  </li>
     *  <li>
     *      Ensures that identical BuildData are narrowed down to different sets
     *      as MultiScm and the Git plugin may sometimes contribute with several
     *      identical sets
     *  </li>
     *  <li>
     *      Ensures that only one relevant set is supplied. It throws in case om ambiguity.
     *  </li>
     * </ul>
     *
     * For a visualized example of several BuilData: See 'docs/More_than_1_gitBuild_data.png'
     * TODO:
     * We don't check that the integration branch complies with the branch specifier,
     * or that commits are heads.
     * See JENKINS-25542, JENKINS-25512, JENKINS-24909
     *
     * @param build The Build
     * @param logger The PrintStream logging object
     * @return The relevant BuildData
     * @throws org.jenkinsci.plugins.pretestedintegration.exceptions.NothingToDoException
     * If no relevant BuildData was found.
     * @throws org.jenkinsci.plugins.pretestedintegration.exceptions.UnsupportedConfigurationException
     * If multiple, ambiguous BuildDatas were found.
     */
    public static BuildData findRelevantBuildData(AbstractBuild<?, ?> build, PrintStream logger, String repoName) throws NothingToDoException, UnsupportedConfigurationException {
        List<BuildData> buildDatas = build.getActions(BuildData.class);
        if (buildDatas.isEmpty()) {
            throw new NothingToDoException("No Git SCM change found.");
        }

        Set<BuildData> relevantBuildData = findRelevantBuildDataImpl(logger, buildDatas, repoName);

        if (relevantBuildData.isEmpty()) {
            String prettyBuildDatasString = toPrettyString(buildDatas);
            throw new NothingToDoException(String.format("No revision matches configuration in 'Integration repository'%n%s", prettyBuildDatasString));
        } else if (relevantBuildData.size() > 1) {
            String prettyBuildDatasString = toPrettyString(relevantBuildData);
            LOGGER.log(Level.SEVERE, String.format("Ambiguous build data found. Matching repository names and multiple changes to integrate.%n%s", prettyBuildDatasString));
            throw new UnsupportedConfigurationException(UnsupportedConfigurationException.AMBIGUITY_IN_BUILD_DATA);
        } else {
            return relevantBuildData.iterator().next();
        }
    }

    /***
     * Returns the relevant BuildDatas from the supplied list of BuildDatas.
     *
     * @param logger PrintStream logger
     * @param buildDatas The list of BuildDatas
     * @param repoName The expanded repoName
     * @return The relevant BuildDatas
     */
    private static Set<BuildData> findRelevantBuildDataImpl( PrintStream logger, List<BuildData> buildDatas, String repoName) {
        Set<BuildData> relevantBuildData = new HashSet<>();
        Set<String> revisions = new HashSet<>(); //Used to detect duplicates

        for (BuildData buildData : buildDatas) {
            if(buildData.lastBuild == null) continue;
                Branch buildBranch = buildData.lastBuild.revision.getBranches().iterator().next();
                String expandedRepository = repoName + "/"; // Assume no trailing slash in configuration
                if (buildBranch.getName().startsWith(expandedRepository)) { // Check integrationBranch matches integration repository
                    String revisionSha = buildData.lastBuild.revision.getSha1String();
                    boolean isDuplicateEntry = !revisions.add(revisionSha); // Check we haven't seen this changeset before
                    if (isDuplicateEntry) {
                        LOGGER.log(Level.INFO, String.format("Revision %s has a duplicate BuildData entry. Using first.", revisionSha));
                    } else {
                        relevantBuildData.add(buildData);
                    }
                }
        }
        return relevantBuildData;
    }

    /***
     * Returns a pretty string listing all the passed in BuildData.
     *
     * @param buildDatas a Collection of BuildData to list
     * @return a string listing all the given BuildData
     */
    private static String toPrettyString(Collection<BuildData> buildDatas) {
        StringBuilder builder = new StringBuilder();
        for (BuildData data : buildDatas) {
            if(data.lastBuild == null){
                builder.append(String.format("No build data for remote:%n%s", data.getRemoteUrls().iterator().next()));
            } else {
                builder.append(String.format(data.lastBuild.revision.getSha1String() + "%n"));
                for (Branch branch : data.lastBuild.revision.getBranches()) {
                    builder.append(String.format(branch.getName() + "%n"));
                }
            }
        }
        return builder.toString();
    }

}
