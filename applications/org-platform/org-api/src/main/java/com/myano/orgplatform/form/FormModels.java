package com.myano.orgplatform.form;

public final class FormModels {
  private FormModels() {}

  public record FormDefinitionDto(
      Long id,
      String code,
      int version,
      String title,
      String schemaJson,
      String listFieldsJson,
      String status,
      String processDefCode) {}

  public record PublishFormRequest(
      String code, String title, String schemaJson, String listFieldsJson, String processDefCode) {}

  public record FormInstanceDto(
      Long id,
      Long formDefId,
      String formCode,
      int formVersion,
      Long orgUnitId,
      String createdBy,
      String status,
      String payloadJson,
      String createdAt) {}

  public record SubmitFormRequest(
      String formCode, String userId, Long orgUnitId, String payloadJson) {}
}
