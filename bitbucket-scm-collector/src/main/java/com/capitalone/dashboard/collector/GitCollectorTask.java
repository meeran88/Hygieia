package com.capitalone.dashboard.collector;


import com.capitalone.dashboard.model.Collector;
import com.capitalone.dashboard.model.CollectorItem;
import com.capitalone.dashboard.model.CollectorType;
import com.capitalone.dashboard.model.Commit;
import com.capitalone.dashboard.model.GitRepo;
import com.capitalone.dashboard.repository.BaseCollectorRepository;
import com.capitalone.dashboard.repository.CommitRepository;
import com.capitalone.dashboard.repository.ComponentRepository;
import com.capitalone.dashboard.repository.GitRepoRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * CollectorTask that fetches Commit information from Git
 */
@Component
public class GitCollectorTask extends CollectorTask<Collector> {
    private static final Log LOG = LogFactory.getLog(GitCollectorTask.class);

    private final BaseCollectorRepository<Collector> collectorRepository;
    private final GitRepoRepository gitRepoRepository;
    private final CommitRepository commitRepository;
    private final GitClient gitClient;
    private final GitSettings gitSettings;
    private final ComponentRepository dbComponentRepository;

    @Autowired
    public GitCollectorTask(TaskScheduler taskScheduler,
                            BaseCollectorRepository<Collector> collectorRepository,
                            GitRepoRepository gitRepoRepository,
                            CommitRepository commitRepository,
                            GitClient gitClient,
                            GitSettings gitSettings,
                            ComponentRepository dbComponentRepository) {
        super(taskScheduler, "Bitbucket");
        this.collectorRepository = collectorRepository;
        this.gitRepoRepository = gitRepoRepository;
        this.commitRepository = commitRepository;
        this.gitClient = gitClient;
        this.gitSettings = gitSettings;
        this.dbComponentRepository = dbComponentRepository;
    }

    @Override
    public Collector getCollector() {
        Collector protoType = new Collector();
        protoType.setName("Bitbucket");
        protoType.setCollectorType(CollectorType.SCM);
        protoType.setOnline(true);
        protoType.setEnabled(true);
        return protoType;
    }

    @Override
    public BaseCollectorRepository<Collector> getCollectorRepository() {
        return collectorRepository;
    }

    @Override
    public String getCron() {
        return gitSettings.getCron();
    }

    /**
     * Clean up unused deployment collector items
     *
     * @param collector the {@link Collector}
     */

    private void clean(Collector collector) {
        Set<ObjectId> uniqueIDs = new HashSet<ObjectId>();
        /**
         * Logic: For each component, retrieve the collector item list of the type SCM.
         * Store their IDs in a unique set ONLY if their collector IDs match with Bitbucket collectors ID.
         */
        for (com.capitalone.dashboard.model.Component comp : dbComponentRepository.findAll()) {
            if (comp.getCollectorItems() == null || comp.getCollectorItems().isEmpty()) continue;
            List<CollectorItem> itemList = comp.getCollectorItems().get(CollectorType.SCM);
            if (itemList == null) continue;
            for (CollectorItem ci : itemList) {
                if (ci != null && ci.getCollectorId().equals(collector.getId())) {
                    uniqueIDs.add(ci.getId());
                }
            }
        }

        /**
         * Logic: Get all the collector items from the collector_item collection for this collector.
         * If their id is in the unique set (above), keep them enabled; else, disable them.
         */
        List<GitRepo> repoList = new ArrayList<GitRepo>();
        Set<ObjectId> gitID = new HashSet<ObjectId>();
        gitID.add(collector.getId());
        for (GitRepo repo : gitRepoRepository.findByCollectorIdIn(gitID)) {
            if (repo != null) {
                repo.setEnabled(uniqueIDs.contains(repo.getId()));
                repoList.add(repo);
            }
        }
        gitRepoRepository.save(repoList);
    }


    @Override
    public void collect(Collector collector) {

        logBanner("Starting...");
        long start = System.currentTimeMillis();
        int repoCount = 0;
        int commitCount = 0;

        clean(collector);
        for (GitRepo repo : enabledRepos(collector)) {
            boolean firstRun = false;
            if (repo.getLastUpdateTime() == null) firstRun = true;
            LOG.debug(repo.getOptions().toString() + "::" + repo.getBranch());
            
            List<String> branches = gitClient.getBranches(repo);
            LOG.info("branch count ==>"+branches.size());
            
            //boolean gotCommits = false;
            
            for(String branchName:branches) {
            	List<Commit> commits = gitClient.getCommits(repo, branchName,firstRun);
            	LOG.info("commit count ==>"+commits.size() + " , branch name ==>"+branchName);
//            	if(!commits.isEmpty()) {
//            		gotCommits = true;
//            	}
            	
            	List<Commit> newCommits = new ArrayList<>();
                for (Commit commit : commits) {
                	if (LOG.isDebugEnabled()) {
                		LOG.debug(commit.getTimestamp() + ":::" + commit.getScmCommitLog());
                	}
                	
                	boolean isNew  = isNewCommit(repo, commit,branchName);
                	//LOG.info("is new ==>"+isNew);
                    if (isNew) {
                        commit.setCollectorItemId(repo.getId());
                        newCommits.add(commit);
                    }
                }
                commitRepository.save(newCommits);
                commitCount += newCommits.size();
            }
            
            
            repo.setLastUpdateTime(Calendar.getInstance().getTime());
//            if (!commits.isEmpty()) {
//            	// It appears that the first commit in the list is the HEAD of the branch
//            	repo.setLastUpdateCommit(commits.get(0).getScmRevisionNumber());
//            }
            
            
            gitRepoRepository.save(repo);

            repoCount++;
        }
        log("Repo Count", start, repoCount);
        log("New Commits", start, commitCount);

        log("Finished", start);
    }

    @SuppressWarnings("unused")
    private Date lastUpdated(GitRepo repo) {
        return repo.getLastUpdateTime();
    }

    private List<GitRepo> enabledRepos(Collector collector) {
        return gitRepoRepository.findEnabledGitRepos(collector.getId());
    }

    private boolean isNewCommit(GitRepo repo, Commit commit,String branchName) {
    	//LOG.info("repoId ==>"+repo.getId());
    	//LOG.info("revisionNUmber "+commit.getScmRevisionNumber());
    	
    	Commit newCommit = commitRepository.findByCollectorItemIdScmRevisionNumberAndBranch(
                repo.getId(), commit.getScmRevisionNumber(),branchName);
    	
    	//LOG.info("commit ==>"+newCommit);
        return newCommit == null;
    }
}
