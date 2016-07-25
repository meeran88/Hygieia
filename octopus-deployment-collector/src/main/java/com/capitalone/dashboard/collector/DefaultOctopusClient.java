package com.capitalone.dashboard.collector;

import java.util.ArrayList;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;

import com.capitalone.dashboard.model.ApplicationDeploymentHistoryItem;
import com.capitalone.dashboard.model.Environment;
import com.capitalone.dashboard.model.Machine;
import com.capitalone.dashboard.model.OctopusApplication;
import com.capitalone.dashboard.model.Release;
import com.capitalone.dashboard.model.Task;
import com.capitalone.dashboard.util.Supplier;

@Component
public class DefaultOctopusClient implements OctopusClient{
	private static final Logger LOGGER = LoggerFactory.getLogger(DefaultOctopusClient.class);
	private final OctopusSettings octopusSettings;
	private final RestOperations restOperations;

	@Autowired
	public DefaultOctopusClient(OctopusSettings octopusSettings,
			Supplier<RestOperations> restOperationsSupplier) {
		this.octopusSettings = octopusSettings;
		this.restOperations = restOperationsSupplier.get();
	}

	@Override
	public List<OctopusApplication> getApplications() {
		List<OctopusApplication> applications = new ArrayList<>();
		boolean hasNext = true;
		String urlPath = "/api/projects";
		while(hasNext) {

			JSONObject resJsonObject =  paresResponse(makeRestCall(octopusSettings.getUrl(),urlPath,octopusSettings.getApiKey()));

			JSONArray jsonArray = (JSONArray)resJsonObject.get("Items");

			for (Object item :jsonArray) {
				JSONObject jsonObject = (JSONObject) item;
				OctopusApplication application = new OctopusApplication();
				application.setInstanceUrl(octopusSettings.getUrl());
				application.setApplicationName(str(jsonObject, "Name"));
				application.setApplicationId(str(jsonObject, "Id"));
				applications.add(application);
			}
			JSONObject links = (JSONObject)resJsonObject.get("Links");
			urlPath = (String)links.get("Page.Next");

			if(urlPath == null || urlPath.isEmpty()) {
				hasNext = false;
			}
		}

		return applications;
	}

	@Override
	public List<Environment> getEnvironments() {
		List<Environment> environments = new ArrayList<>();
		boolean hasNext = true;
		String urlPath = "/api/environments";
		while(hasNext) {
			JSONObject resJsonObject =  paresResponse(makeRestCall(octopusSettings.getUrl(),
					urlPath,octopusSettings.getApiKey()));
			JSONArray jsonArray = (JSONArray)resJsonObject.get("Items");
			for (Object item : jsonArray) {
				JSONObject jsonObject = (JSONObject) item;
				environments.add(new Environment(str(jsonObject, "Id"), str(
						jsonObject, "Name")));
			}

			JSONObject links = (JSONObject)resJsonObject.get("Links");
			urlPath = (String)links.get("Page.Next");

			if(urlPath == null || urlPath.isEmpty()) {
				hasNext = false;
			}

		}
		return environments;
	}

	@Override
	public List<ApplicationDeploymentHistoryItem> getApplicationDeploymentHistory(OctopusApplication application) {
		List<ApplicationDeploymentHistoryItem> applicationDeployments = new ArrayList<>();

		boolean hasNext = true;
		String urlPath = "/api/deployments?projects="+application.getApplicationId();
		while(hasNext) {

			JSONObject resJsonObject =  paresResponse(makeRestCall(octopusSettings.getUrl(),
					urlPath,octopusSettings.getApiKey()));

			JSONArray jsonArray = (JSONArray)resJsonObject.get("Items");

			LOGGER.info("applicationID ==>"+application.getApplicationId());
			LOGGER.info("Deployment History size ==>"+jsonArray.size());

			for (Object item :jsonArray) {
				JSONObject jsonObject = (JSONObject) item;
				//LOGGER.info("Project Id ==>"+str(jsonObject, "ProjectId"));

				ApplicationDeploymentHistoryItem historyItem = new ApplicationDeploymentHistoryItem();
				historyItem.setApplicationId(application.getApplicationId());
				historyItem.setApplicationName(application.getApplicationName());
				historyItem.setEnvironmentId(str(jsonObject, "EnvironmentId"));
				historyItem.setDeploymentId(str(jsonObject, "Id"));

				historyItem.setCollectorItemId(application.getId());

				Environment env = getEnvironmentById(historyItem.getEnvironmentId());
				historyItem.setEnvironmentName(env.getName());


				Release rel = getReleaseById(str(jsonObject, "ReleaseId"));

				historyItem.setVersion(rel.getVersion());




				//historyItem.setAsOfDate(System.currentTimeMillis());// for testing

				String pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
				DateTimeFormatter dtf = DateTimeFormat.forPattern(pattern);
				DateTime dateTime = dtf.parseDateTime(str(jsonObject, "Created"));

				historyItem.setAsOfDate(dateTime.getMillis());

				Task task = getTaskById(str(jsonObject, "TaskId"));
				historyItem.setDeployed(task.isState());


				// getting list of machines
				JSONArray specificMachineIds = (JSONArray) jsonObject.get("SpecificMachineIds");
				if(specificMachineIds.size() == 0) {
					List<Machine> machines = getMachinesByEnvId(historyItem.getEnvironmentId());
					historyItem.setMachines(machines);
				} else {
					List<Machine> machines = new ArrayList<Machine>();
					for (Object obj :specificMachineIds) {
						String machineId = (String)obj;
						Machine m = getMachineById(machineId, historyItem.getEnvironmentId());
					   machines.add(m);
					}
					historyItem.setMachines(machines);
				}

				applicationDeployments.add(historyItem);


			}

			JSONObject links = (JSONObject)resJsonObject.get("Links");
			urlPath = (String)links.get("Page.Next");

			if(urlPath == null || urlPath.isEmpty()) {
				hasNext = false;
			}

		}

		return applicationDeployments;
	}

	private Task getTaskById(String taskId) {
		JSONObject resJsonObject =  paresResponse(makeRestCall(octopusSettings.getUrl(),
				"/api/tasks/"+taskId,octopusSettings.getApiKey()));
		Task task = new Task();

		task.setTaskId(taskId);
		task.setTaskName((String)resJsonObject.get("Name"));
		String state = (String)resJsonObject.get("State");
		if(state.equals("Failed")) {
			task.setState(false);
		} else {
			task.setState(true);
		}


		return task;
	}
	
	private Machine getMachineById(String machineId,String envId) {
		JSONObject resJsonObject =  paresResponse(makeRestCall(octopusSettings.getUrl(),
				"/api/machines/"+machineId,octopusSettings.getApiKey()));
		Machine machine = new Machine();
		machine.setEnviromentId(envId);
		machine.setMachineName((String)resJsonObject.get("Name"));
		machine.setMachineId((String)resJsonObject.get("Id"));
		String status = (String)resJsonObject.get("Status");
		if(status.equals("Online")) {
			machine.setStatus(true);
		} else {
			machine.setStatus(false);	
		}
		
		return machine;
		
	}

	private List<Machine> getMachinesByEnvId(String envId) {

		List<Machine> machines= new ArrayList<Machine>();

		boolean hasNext = true;
		String urlPath = "/api/environments/"+envId+"/machines";
		while(hasNext) {

			JSONObject resJsonObject =  paresResponse(makeRestCall(octopusSettings.getUrl(),
					urlPath,octopusSettings.getApiKey()));

			JSONArray jsonArray = (JSONArray)resJsonObject.get("Items");
			for (Object item :jsonArray) {
				JSONObject jsonObject = (JSONObject) item;
				Machine machine = new Machine();
				machine.setEnviromentId(envId);
				machine.setMachineName((String)jsonObject.get("Name"));
				machine.setMachineId((String)jsonObject.get("Id"));
				String status = (String)jsonObject.get("Status");
				if(status.equals("Online")) {
					machine.setStatus(true);
				} else {
					machine.setStatus(false);	
				}
				machines.add(machine);
			}

			JSONObject links = (JSONObject)resJsonObject.get("Links");
			urlPath = (String)links.get("Page.Next");

			if(urlPath == null || urlPath.isEmpty()) {
				hasNext = false;
			}
		}

		return machines;
	}

	private Release getReleaseById(String id) {
		JSONObject resJsonObject =  paresResponse(makeRestCall(octopusSettings.getUrl(),
				"/api/releases/"+id,octopusSettings.getApiKey()));
		Release rel = new Release();

		rel.setApplicationId((String)resJsonObject.get("ProjectId"));
		rel.setReleaseId((String)resJsonObject.get("Id"));
		rel.setVersion((String)resJsonObject.get("Version"));

		return rel;
	}

	private Environment getEnvironmentById(String envId){

		JSONObject resJsonObject =  paresResponse(makeRestCall(octopusSettings.getUrl(),
				"/api/environments/"+envId,octopusSettings.getApiKey()));
		Environment env = new Environment(envId, (String)resJsonObject.get("Name"));

		return env;



	}

	private ResponseEntity<String> makeRestCall(String instanceUrl,
			String endpoint,String apiKey) {
		String url = instanceUrl+ endpoint;
		ResponseEntity<String> response = null;
		try {
			response = restOperations.exchange(url, HttpMethod.GET,
					new HttpEntity<>(createHeaders("X-Octopus-ApiKey",apiKey)), String.class);

		} catch (RestClientException re) {
			LOGGER.error("Error with REST url: " + url);
			LOGGER.error(re.getMessage());
		}
		return response;
	}

	protected HttpHeaders createHeaders(String headerName,String headerValue) {

		HttpHeaders headers = new HttpHeaders();
		headers.set(headerName, headerValue);
		return headers;
	}

	private JSONObject paresResponse(ResponseEntity<String> response) {
		if (response == null)
			return new JSONObject();
		try {

			JSONObject jsonObject = (JSONObject)new JSONParser().parse(response.getBody());
			return jsonObject;

		} catch (ParseException pe) {
			LOGGER.debug(response.getBody());
			LOGGER.error(pe.getMessage());
		}
		return new JSONObject();
	}

	private String str(JSONObject json, String key) {
		Object value = json.get(key);
		return value == null ? null : value.toString();
	}





}
