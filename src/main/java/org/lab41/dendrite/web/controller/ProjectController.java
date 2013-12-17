package org.lab41.dendrite.web.controller;

import org.lab41.dendrite.models.GraphMetadata;
import org.lab41.dendrite.models.ProjectMetadata;
import org.lab41.dendrite.services.MetadataService;
import org.lab41.dendrite.services.MetadataTx;
import org.lab41.dendrite.web.beans.ProjectBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/api")
public class ProjectController {

    Logger logger = LoggerFactory.getLogger(ProjectController.class);

    @Autowired
    MetadataService metadataService;

    @RequestMapping(value = "/projects", method = RequestMethod.GET)
    public @ResponseBody Map<String, Object> getProjects() {

        MetadataTx tx = metadataService.newTransaction();

        Map<String, Object> response = new HashMap<>();
        ArrayList<Object> projects = new ArrayList<>();
        response.put("projects", projects);

        for(ProjectMetadata projectMetadata: tx.getProjects()) {
            projects.add(getProjectMap(projectMetadata));
        }

        // Commit must come after all graph access.
        tx.commit();

        return response;
    }

    @RequestMapping(value = "/projects/{projectId}", method = RequestMethod.GET)
    public ResponseEntity<Map<String, Object>> getProject(@PathVariable String projectId) {

        Map<String, Object> response = new HashMap<>();
        MetadataTx tx = metadataService.newTransaction();
        ProjectMetadata projectMetadata = tx.getProject(projectId);

        if (projectMetadata == null) {
            response.put("status", "error");
            response.put("msg", "could not find project '" + projectId + "'");
            tx.rollback();
            return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
        }

        response.put("project", getProjectMap(projectMetadata));

        // Commit must come after all graph access.
        tx.commit();

        return new ResponseEntity<>(response, HttpStatus.OK);

    }

    @RequestMapping(value = "/projects", method = RequestMethod.POST)
    public ResponseEntity<Map<String, Object>> createProject(@Valid @RequestBody ProjectBean item,
                                                             BindingResult result,
                                                             UriComponentsBuilder builder) {

        Map<String, Object> response = new HashMap<>();

        if (result.hasErrors()) {
            response.put("status", "error");
            response.put("msg", result.toString());
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        String name = item.getName();

        MetadataTx tx = metadataService.newTransaction();

        ProjectMetadata projectMetadata = tx.createProject();
        projectMetadata.setName(name);

        GraphMetadata graphMetadata = projectMetadata.getCurrentGraph();
        graphMetadata.setProject(projectMetadata);

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(builder.path("/{projectId}").buildAndExpand(projectMetadata.getId()).toUri());

        response.put("project", getProjectMap(projectMetadata));

        // Commit must come after all graph access.
        tx.commit();

        return new ResponseEntity<>(response, headers, HttpStatus.CREATED);
    }

    @RequestMapping(value = "/projects/{projectId}", method = RequestMethod.DELETE)
    public ResponseEntity<Map<String, Object>> deleteProject(@PathVariable String projectId) {

        MetadataTx tx = metadataService.newTransaction();

        Map<String, Object> response = new HashMap<>();

        ProjectMetadata projectMetadata = tx.getProject(projectId);
        if (projectMetadata == null) {
            response.put("status", "error");
            response.put("msg", "could not find project '" + projectId + "'");
            tx.rollback();
            return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
        }

        try {
            tx.deleteProject(projectMetadata);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("msg", e.toString());
            tx.rollback();
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        response.put("msg", "deleted");

        // Commit must come after all graph access.
        tx.commit();

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    private Map<String, Object> getProjectMap(ProjectMetadata projectMetadata) {
        Map<String, Object> project = new HashMap<>();

        String id = projectMetadata.getId();
        project.put("_id", id);
        project.put("name", projectMetadata.getName());
        project.put("current_graph", projectMetadata.getCurrentGraph().getId());

        return project;
    }
}
