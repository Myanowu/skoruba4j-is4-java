package com.myano.orgplatform.form;

import com.myano.orgplatform.form.FormModels.FormDefinitionDto;
import com.myano.orgplatform.form.FormModels.FormInstanceDto;
import com.myano.orgplatform.form.FormModels.PublishFormRequest;
import com.myano.orgplatform.form.FormModels.SubmitFormRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/forms")
public class FormController {

  private final FormService formService;

  public FormController(FormService formService) {
    this.formService = formService;
  }

  @GetMapping("/definitions")
  public List<FormDefinitionDto> definitions() {
    return formService.listPublished();
  }

  @PostMapping("/definitions/publish")
  public FormDefinitionDto publish(@RequestBody PublishFormRequest body) {
    return formService.publish(body);
  }

  @PostMapping("/submit")
  public FormInstanceDto submit(@RequestBody SubmitFormRequest body) {
    return formService.submit(body);
  }

  @GetMapping("/instances/{id}")
  public FormInstanceDto get(@PathVariable long id) {
    return formService.get(id);
  }

  @GetMapping("/instances/mine")
  public List<FormInstanceDto> mine(@RequestParam String userId) {
    return formService.mine(userId);
  }

  @GetMapping("/instances/org")
  public List<FormInstanceDto> byOrg(@RequestParam String orgPathPrefix) {
    return formService.byOrgPathPrefix(orgPathPrefix);
  }
}
