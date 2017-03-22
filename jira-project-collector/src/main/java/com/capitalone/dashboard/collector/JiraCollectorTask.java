package com.capitalone.dashboard.collector;

/**
 * Created by vinod on 8/9/16.
 */


import com.atlassian.jira.rest.client.api.domain.BasicProject;
import com.capitalone.dashboard.model.Collector;
import com.capitalone.dashboard.model.CollectorItem;
import com.capitalone.dashboard.model.CollectorType;
import com.capitalone.dashboard.model.DefectInjection;
import com.capitalone.dashboard.model.JiraRepo;
import com.capitalone.dashboard.model.ProjectVersionIssues;
import com.capitalone.dashboard.model.Sprint;
import com.capitalone.dashboard.model.SprintVelocity;
import com.capitalone.dashboard.repository.BaseCollectorRepository;
import com.capitalone.dashboard.repository.ComponentRepository;
import com.capitalone.dashboard.repository.DefectInjectsRepository;
import com.capitalone.dashboard.repository.JiraProjectRepository;
import com.capitalone.dashboard.repository.ProjectVersionRepository;
import com.capitalone.dashboard.repository.SprintVelocityRepository;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bson.types.ObjectId;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * CollectorTask that fetches Project Version Issues information from Jira
 */
@Component
public class JiraCollectorTask extends CollectorTask<Collector> {
    private static final Log LOG = LogFactory.getLog(JiraCollectorTask.class);

    private final BaseCollectorRepository<Collector> collectorRepository;
    private final JiraProjectRepository jiraprojectrepository;
    private final ProjectVersionRepository projectversionrepository;
    private final SprintVelocityRepository sprintVelocityRepository;
    private final JiraClient jiraclient;
    private final JiraSettings jirasettings;
    private final DefectInjectsRepository defectInjectsRepository;
    private final ComponentRepository dbComponentRepository;



    @Autowired
    public JiraCollectorTask(TaskScheduler taskScheduler,
                             BaseCollectorRepository<Collector> collectorRepository,
                             JiraProjectRepository jiraprojectrepository,
                             ProjectVersionRepository projectversionrepository,
                             SprintVelocityRepository sprintVelocityRepository,
                             JiraClient jiraclient,
                             JiraSettings jirasettings,
                             DefectInjectsRepository defectInjectsRepository,
                             ComponentRepository dbComponentRepository) {
        super(taskScheduler, "Jiraproject");
        this.collectorRepository = collectorRepository;
        this.jiraclient = jiraclient;
        this.jiraprojectrepository = jiraprojectrepository;
        this.projectversionrepository = projectversionrepository;
        this.sprintVelocityRepository=sprintVelocityRepository;
        this.defectInjectsRepository=defectInjectsRepository;
        this.jirasettings = jirasettings;
        this.dbComponentRepository = dbComponentRepository;
    }

    @Override
    public Collector getCollector() {
        Collector protoType = new Collector();
        protoType.setName("Jiraproject");
        protoType.setCollectorType(CollectorType.Jiraproject);
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
        return jirasettings.getCron();
    }

    private void clean(Collector collector) {
        Set<ObjectId> uniqueIDs = new HashSet<ObjectId>();
        /**
         * Logic: For each component, retrieve the collector item list of the type SCM.
         * Store their IDs in a unique set ONLY if their collector IDs match with Bitbucket collectors ID.
         */
        List<com.capitalone.dashboard.model.Component> comps = (List<com.capitalone.dashboard.model.Component>) dbComponentRepository.findAll();
        for (com.capitalone.dashboard.model.Component comp : dbComponentRepository.findAll()) {
            if (comp.getCollectorItems() == null || comp.getCollectorItems().isEmpty()) continue;
            List<CollectorItem> itemList = comp.getCollectorItems().get(CollectorType.Jiraproject);
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
        List<JiraRepo> repoList = new ArrayList<JiraRepo>();
        Set<ObjectId> jiraID = new HashSet<ObjectId>();
        jiraID.add(collector.getId());
        for (JiraRepo repo : jiraprojectrepository.findByCollectorIdIn(jiraID)) {
            if (repo != null) {
                repo.setEnabled(uniqueIDs.contains(repo.getId()));
                repoList.add(repo);
            }
        }
        jiraprojectrepository.save(repoList);
    }

    @Override
    public void collect(Collector collector) {
        logBanner("Jira Project Collector..starting");
        long start = System.currentTimeMillis();
        int projectCount = 0;
        int issueCount = 0;
        int scannedProjects = 0;
        int newProjects = 0;
        int updatedSprintVelocities=0;
        int updatedDefectInjections=0;
        int newSprintVelocities=0;
        int newDefectInjections=0;
        clean(collector);


        /*List<JiraRepo> fetchedprojects = jiraclient.getProjects();
        List<JiraRepo> repoList = new ArrayList<JiraRepo>();
        for(JiraRepo repo : fetchedprojects){
            // LOG.info(jiraprojectrepository.findJiraRepo(collector.getId(),repo.getVERSIONID(),repo.getPROJECTID()) == null);
            JiraRepo savedRepo = jiraprojectrepository.findJiraRepo(collector.getId(),repo.getVERSIONID(),repo.getPROJECTID());
            //LOG.info(collector.getId() + " " + repo.getVERSIONID() + " " + repo.getPROJECTID());

            if(savedRepo == null){
                repo.setCollectorId(collector.getId());
                repo.setEnabled(true);

              //  LOG.info("To Add:" + repo.getPROJECTNAME() + " " + repo.getVERSIONNAME());
                repoList.add(repo);
                try {
                    jiraprojectrepository.save(repo);
                    newProjects++;
                }catch(Exception e){
                    LOG.info(e);
                }
            }
            scannedProjects++;
        }*/

        LOG.info("New Projects:" + newProjects);

        int enabledVersions = 0;
        int newIssues = 0;
        int updatedIssues = 0;
        LOG.info("TOTAL ENABLED REPOS : "+ enabledRepos(collector));
        
        for(JiraRepo repo : enabledRepos(collector)){
        	
            boolean firstRun = false;
           // LOG.info("Enabled repo:");
           // LOG.info(repo);
           // enabledrepoList.add(repo);
            
            JiraRepo repoSlippage = jiraclient.getDefectSlippage(repo);
            
            /*repo.setStageDefects(repoSlippage.getStageDefects());
            repo.setProdDefects(repoSlippage.getProdDefects());*/
            repo.setPreQADefects(repoSlippage.getPreQADefects());
            repo.setPostQADefects(repoSlippage.getPostQADefects());
            
            
            // geting active sprint 
            Sprint s = jiraclient.getActiveSprint(repo);
            if(s != null) {
            	repo.setACTIVE_SPRINT_ID(s.getSprintId());
            	repo.setACTIVE_SPRINT_NAME(s.getSprintName());
            	repo.setACTIVE_SPRINT_START_TIME(s.getStartTime());
            	repo.setACTIVE_SPRINT_END_TIME(s.getEndTime());
            	repo.setRAPIDVIEWID(s.getActiveBoardId());
            }
            
            List<ProjectVersionIssues> enabledProjectVersionIssues  = jiraclient.getprojectversionissues(repo,firstRun);
           // System.out.println("ISSUES COLLECTED SIZE "  + enabledProjectVersionIssues.size());
            for(ProjectVersionIssues pvi : enabledProjectVersionIssues){
                ProjectVersionIssues savedIssue = projectversionrepository.findByCollectorItemIdAndIssueId(collector.getId(),pvi.getIssueId());
                if(savedIssue != null){
                    savedIssue.setIssueDescription(pvi.getIssueDescription());
                    savedIssue.setIssueStatus(pvi.getIssueStatus());
                    savedIssue.setSprintId(pvi.getSprintId());
                    savedIssue.setSprintName(pvi.getSprintName());
                    savedIssue.setStoryPoint(pvi.getStoryPoint());
                  //  savedIssue.setChangeDate()
                    projectversionrepository.save(savedIssue);
                   // LOG.info("Updated Issue " + pvi.getIssueDescription());
                    updatedIssues++;
                }
                else{
                    pvi.setCollectorItemId(collector.getId());
                  //  LOG.info("Created Issue " + pvi.getIssueDescription());
                    projectversionrepository.save(pvi);
                    newIssues++;
                }
            }

            

            
            List<SprintVelocity> sprintVelocities = null;
			try {
				sprintVelocities = jiraclient.getVelocityReportByProject(repo);
			} catch (ParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}      

            Collections.reverse(sprintVelocities);
            List<SprintVelocity> prevVelocities = sprintVelocityRepository
    				.findVelocityReport(collector.getId(),repo.getVERSIONID(),repo.getPROJECTID());
           
            if(prevVelocities.size()!=0 && sprintVelocities.size() != prevVelocities.size())
            {
            	for(SprintVelocity entity : prevVelocities)
            	{            	
            	sprintVelocityRepository.delete(entity);
            	}
            }
        
           

            if(sprintVelocities != null)
            {
            for(SprintVelocity sv : sprintVelocities){
                SprintVelocity savedSprintVelocity = sprintVelocityRepository.findSprintVelocityByCollectorIdSprintId(collector.getId(),sv.getSprintId(),sv.getVersionId(), sv.getProjectId());
                if(savedSprintVelocity != null){
                    savedSprintVelocity.setCommitted(sv.getCommitted());
                    savedSprintVelocity.setCompleted(sv.getCompleted());
                    savedSprintVelocity.setCompletedSum(sv.getCompletedSum());
                    savedSprintVelocity.setNotCompletedSum(sv.getNotCompletedSum());
                    savedSprintVelocity.setOutOfSprintSum(sv.getOutOfSprintSum());
                    savedSprintVelocity.setAllIssuesSum(sv.getAllIssuesSum());
                    savedSprintVelocity.setPuntedSum(sv.getPuntedSum());
                    sprintVelocityRepository.save(savedSprintVelocity);
                   
                    updatedSprintVelocities++;
                }
                else{
                    sv.setCollectorItemId(collector.getId());
                    
                    sprintVelocityRepository.save(sv);
                    newSprintVelocities++;
                }
            }

            }           
            
            List<DefectInjection> defectInjects = jiraclient.getDefectInjections(sprintVelocities);
            List<DefectInjection> diList = defectInjectsRepository.findDefectInjection(collector.getId(),repo.getPROJECTID(),repo.getVERSIONID());
            
            if(diList.size()!=0 && diList.size() != defectInjects.size())
            {
            	for(DefectInjection entity : diList)
            	{            	
            		defectInjectsRepository.delete(entity);
            	}
            }
            
            if(defectInjects != null)
            {
            for(DefectInjection di : defectInjects){
            	
            	DefectInjection savedDefectInjection = defectInjectsRepository.findDefectInjectionByCollectorIdSprintId(collector.getId(),di.getSprintId(),di.getVersionId(), di.getProjectId());
                 if(savedDefectInjection != null){
                	 savedDefectInjection.setDefectCount(di.getDefectCount());
                	 savedDefectInjection.setAchievedPoints(di.getAchievedPoints());
                     
                	 defectInjectsRepository.save(savedDefectInjection);
                    
                     updatedDefectInjections++;
                 }
                 else
                 {
            	 di.setCollectorItemId(collector.getId());
            	 defectInjectsRepository.save(di);   
            	 newDefectInjections++;
                 }
            }
            }
            
            enabledVersions++;
            jiraprojectrepository.save(repo);
            
            
        }
        LOG.info("Enabled Projects Versions:" + enabledVersions);
        LOG.info("New Issues:" + newIssues);
        LOG.info("Updated Issues:" + updatedIssues);
        LOG.info("New sprint velocities:" + newSprintVelocities);
        LOG.info("Updated sprint velocities:" + updatedSprintVelocities);
        LOG.info("Total Issues:" + (newIssues + updatedIssues));
        LOG.info("Finished.");

        //fetching issues for enabled versions


    }


    private List<JiraRepo> enabledRepos(Collector collector) {
        return jiraprojectrepository.findEnabledJiraRepos(collector.getId());   //gitRepoRepository.findEnabledGitRepos(collector.getId());
    }

    private boolean isNewProjectVersionIssue(JiraRepo repo, ProjectVersionIssues projectversionissues){
        // ProjectVersionIssues newprojectversionissues =  projectversionrepository.findByCollectorItemIdAndIssueId(repo.getId(),projectversionissues.getIssueID());
        //LOG.info("commit ==>"+newCommit);
        return false;//newprojectversionissues == null;
    }
}
