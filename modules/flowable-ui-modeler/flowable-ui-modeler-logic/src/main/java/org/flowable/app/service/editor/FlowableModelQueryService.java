/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.flowable.app.service.editor;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.activiti.editor.language.json.converter.RDSBpmnJsonConverter;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.flowable.app.constant.BpmnFile;
import org.flowable.app.constant.RdsForms;
import org.flowable.app.domain.editor.AbstractModel;
import org.flowable.app.domain.editor.AppDefinition;
import org.flowable.app.domain.editor.Model;
import org.flowable.app.model.common.ResultListDataRepresentation;
import org.flowable.app.model.editor.AppDefinitionListModelRepresentation;
import org.flowable.app.model.editor.ModelRepresentation;
import org.flowable.app.repository.editor.ModelRepository;
import org.flowable.app.repository.editor.ModelSort;
import org.flowable.app.security.SecurityUtils;
import org.flowable.app.service.api.ModelService;
import org.flowable.app.service.exception.BadRequestException;
import org.flowable.app.service.exception.InternalServerErrorException;
import org.flowable.app.util.XmlUtil;
import org.flowable.bpmn.BpmnAutoLayout;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.Process;
import org.flowable.editor.language.json.converter.util.CollectionUtils;
import org.flowable.editor.language.json.model.ModelInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * @author Tijs Rademakers
 */
@Service
@Transactional
public class FlowableModelQueryService {

    private static final Logger logger = LoggerFactory.getLogger(FlowableModelQueryService.class);

    protected static final String FILTER_SHARED_WITH_ME = "sharedWithMe";
    protected static final String FILTER_SHARED_WITH_OTHERS = "sharedWithOthers";
    protected static final String FILTER_FAVORITE = "favorite";

    protected static final int MIN_FILTER_LENGTH = 1;

    @Autowired
    protected ModelRepository modelRepository;

    @Autowired
    protected ModelService modelService;

    @Autowired
    protected ObjectMapper objectMapper;

    protected BpmnXMLConverter bpmnXmlConverter = new BpmnXMLConverter();
    protected RDSBpmnJsonConverter bpmnJsonConverter = new RDSBpmnJsonConverter();

    public ResultListDataRepresentation getModels(String filter, String sort, Integer modelType, HttpServletRequest request) {

        // need to parse the filterText parameter ourselves, due to encoding issues with the default parsing.
        String filterText = null;
        String tagId = null;
        List<NameValuePair> params = URLEncodedUtils.parse(request.getQueryString(), Charset.forName("UTF-8"));
        if (params != null) {
            for (NameValuePair nameValuePair : params) {
                if ("filterText".equalsIgnoreCase(nameValuePair.getName())) {
                    filterText = nameValuePair.getValue();
                } else if("tagId".equalsIgnoreCase(nameValuePair.getName())) {
                  tagId = nameValuePair.getValue();
                }
            }
        }

        List<ModelRepresentation> resultList = new ArrayList<>();
        List<Model> models = null;

        String validFilter = makeValidFilterText(filterText);

        if(tagId !=null) {
          models = modelRepository.findByModelTypeAndTag(modelType, tagId, sort);
        } else if (validFilter != null) {
            models = modelRepository.findByModelTypeAndFilter(modelType, validFilter, sort);

        } else {
            models = modelRepository.findByModelType(modelType, sort);
        }

        if (CollectionUtils.isNotEmpty(models)) {
            List<String> addedModelIds = new ArrayList<String>();
            for (Model model : models) {
                if (!addedModelIds.contains(model.getId())) {
                    addedModelIds.add(model.getId());
                    ModelRepresentation representation = createModelRepresentation(model);
                    resultList.add(representation);
                }
            }
        }

        ResultListDataRepresentation result = new ResultListDataRepresentation(resultList);
        return result;
    }

    public ResultListDataRepresentation getModelsToIncludeInAppDefinition() {

        List<ModelRepresentation> resultList = new ArrayList<>();

        List<String> addedModelIds = new ArrayList<>();
        List<Model> models = modelRepository.findByModelType(AbstractModel.MODEL_TYPE_BPMN, ModelSort.MODIFIED_DESC);

        if (CollectionUtils.isNotEmpty(models)) {
            for (Model model : models) {
                if (!addedModelIds.contains(model.getId())) {
                    addedModelIds.add(model.getId());
                    ModelRepresentation representation = createModelRepresentation(model);
                    resultList.add(representation);
                }
            }
        }

        ResultListDataRepresentation result = new ResultListDataRepresentation(resultList);
        return result;
    }

  public Object importProcessModel(HttpServletRequest request, MultipartFile file, boolean overwrite)
  {
    try
    {
      BpmnModel bpmnModel = readFromFile(file);

      // Auto format the bpmn if not location data is attached to the model already
      if (MapUtils.isEmpty(bpmnModel.getLocationMap()))
      {
        new BpmnAutoLayout(bpmnModel).execute();
      }

      Process process = getMainProcessFromBpmnModel(bpmnModel, overwrite);
      Map<String, ModelInfo> formKeyMap = validateAndSaveForms(process, overwrite);
      return createOrUpdateModel(bpmnModel, process, formKeyMap);
    }
    catch (BpmnFileValidationException e)
    {
      return e.validationErrors;
    }
    catch (Exception e)
    {
      logger.error("An error occurred while importing Bpmn file!", e);
      throw new BadRequestException("An error occurred while importing Bpmn file [" + e.getMessage() + "]", e);
    }
  }

  private BpmnModel readFromFile(MultipartFile bpmnFile) throws BpmnFileException, BpmnFileValidationException
  {
    String fileName = bpmnFile.getOriginalFilename();
    assertFileType(fileName);

    XMLInputFactory xif = XmlUtil.createSafeXmlInputFactory();
    XMLStreamReader xtr = null;

    try (InputStream inputStream = bpmnFile.getInputStream();
        InputStreamReader xmlIn = new InputStreamReader(inputStream, Charset.forName("UTF-8")))
    {
      xtr = xif.createXMLStreamReader(xmlIn);
      BpmnModel bpmnModel = bpmnXmlConverter.convertToBpmnModel(xtr);

      if (CollectionUtils.isEmpty(bpmnModel.getProcesses()))
      {
        throw new BpmnFileValidationException(
            Collections.<String, Object>singletonMap("message", "No process found in definition [" + fileName + "]"));
      }

      return bpmnModel;
    }
    catch (IOException e)
    {
      throw new BpmnFileException("Error reading file [" + fileName + "]", e);
    }
    catch (XMLStreamException e)
    {
      throw new BpmnFileException("Unable to create XMLStreamReader for file [" + fileName + "]", e);
    }
    finally
    {
      try
      {
        if (xtr != null)
        {
          xtr.close();
        }
      }
      catch (XMLStreamException e)
      {
        throw new BpmnFileException("Unable to close XMLStreamReader", e);
      }
    }
  }

  private Process getMainProcessFromBpmnModel(BpmnModel bpmnModel, boolean overwrite) throws BpmnFileValidationException
  {
    // Check if the process already exists before importing it
    Process process = bpmnModel.getMainProcess();
    Map<String, Object> validationErrors = new HashMap<>();

    if (process == null)
    {
      throw new BpmnFileValidationException(
          Collections.<String, Object>singletonMap("message", "Main business process is null"));
    }

    List existingProcessModels = modelRepository.findByKeyAndType(process.getId(), AbstractModel.MODEL_TYPE_BPMN);
    if (!overwrite && CollectionUtils.isNotEmpty(existingProcessModels))
    {
      validationErrors.put("message", "Process with same key already exists");
      validationErrors.put("existingProcess", process.getId());
      throw new BpmnFileValidationException(validationErrors);
    }

    return process;
  }

  private Map<String, ModelInfo> validateAndSaveForms(Process process, boolean overwrite)
      throws BpmnFileValidationException
  {
    Map<String, ModelInfo> formKeyMap = new HashMap<>();
    List<String> existingForms = new ArrayList<>();
    List<ExtensionElement> forms = process.getExtensionElements().get(RdsForms.FORM_ELEMENT);

    if(CollectionUtils.isNotEmpty(forms))
    {
      //First find any existing form with same key
      for (ExtensionElement eeForm : forms)
      {
        String formkey = eeForm.getAttributeValue(null, RdsForms.FORM_KEY);
        List existingFormModels = modelRepository.findByKeyAndType(formkey, AbstractModel.MODEL_TYPE_FORM_RDS);
        if (CollectionUtils.isNotEmpty(existingFormModels))
        {
          existingForms.add(formkey);
        }
      }

      // Throw validation exception if any form with same form key exists and overwrite is false
      if (!overwrite && CollectionUtils.isNotEmpty(existingForms))
      {
        Map<String, Object> validationErrors = new HashMap<>();
        validationErrors.put("message", "Form(s) with the same key already exist");
        validationErrors.put("existingForms", existingForms);
        throw new BpmnFileValidationException(validationErrors);
      }

      for (ExtensionElement eeForm : forms)
      {
        String formkey = eeForm.getAttributeValue(null, RdsForms.FORM_KEY);
        String formName = eeForm.getAttributeValue(null, RdsForms.FORM_NAME);
        formName = StringUtils.isBlank(formName) ? formkey : formName;

        String formDesc = eeForm.getAttributeValue(null, RdsForms.FORM_DESCRIPTION);
        String formDefinition = eeForm.getElementText();

        ModelRepresentation model = new ModelRepresentation();
        model.setKey(formkey);
        model.setName(formName);
        model.setDescription(formDesc);
        model.setModelType(AbstractModel.MODEL_TYPE_FORM_RDS);

        List<Model> existingFormModels = modelRepository.findByKeyAndType(formkey, AbstractModel.MODEL_TYPE_FORM_RDS);
        Model formModel;
        if (CollectionUtils.isNotEmpty(existingFormModels))
        {
          formModel = existingFormModels.get(0);
          modelService
              .saveModel(formModel, formDefinition, null, true, "imported",
                  SecurityUtils.getCurrentUserObject());
        }
        else
        {
          formModel = modelService
              .createModel(model, formDefinition, SecurityUtils.getCurrentUserObject());
        }

        ModelInfo modelInfo = new ModelInfo(formModel.getId(), formModel.getName(), formkey);
        formKeyMap.put(formkey, modelInfo);
      }
    }

    return formKeyMap;
  }

  private ModelRepresentation createOrUpdateModel(BpmnModel bpmnModel, Process process,
      Map<String, ModelInfo> formKeyMap)
  {
    ObjectNode modelNode = bpmnJsonConverter.convertToJson(bpmnModel, formKeyMap, null);
    String name = StringUtils.isNotEmpty(process.getName()) ? process.getName() : process.getId();
    String description = process.getDocumentation();

    ModelRepresentation model = new ModelRepresentation();
    model.setKey(process.getId());
    model.setName(name);
    model.setDescription(description);
    model.setModelType(AbstractModel.MODEL_TYPE_BPMN);

    List<Model> existingProcessModels = modelRepository
        .findByKeyAndType(process.getId(), AbstractModel.MODEL_TYPE_BPMN);

    Model modelToReturn;
    if (existingProcessModels.size() > 0)
    {
      modelToReturn = existingProcessModels.get(0);
      modelService.saveModel(modelToReturn, modelNode.toString(), null, true, "imported",
          SecurityUtils.getCurrentUserObject());
    }
    else
    {
      modelToReturn = modelService
          .createModel(model, modelNode.toString(), SecurityUtils.getCurrentUserObject());
    }
    return new ModelRepresentation(modelToReturn);
  }

  private static class BpmnFileException extends Exception
  {
    public BpmnFileException(String message)
    {
      super(message);
    }

    BpmnFileException(String message, Throwable cause)
    {
      super(message, cause);
    }
  }

  private static class BpmnFileValidationException extends Exception
  {
    private Map<String, Object> validationErrors;

    BpmnFileValidationException(Map<String, Object> validationErrors)
    {
      super("There are validation errors!");
      this.validationErrors = validationErrors;
    }
  }

  private void assertFileType(String fileName) throws BpmnFileValidationException
  {
    //file "endings" because they aren't really an extension
    List<String> validFileEndings = Arrays.asList(BpmnFile.BPMN20_XML_ENDING, BpmnFile.BPMN_EXT);

    if (StringUtils.isBlank(fileName) || !endsWithOneOf(fileName, validFileEndings))
    {
      throw new BpmnFileValidationException(Collections.<String, Object>singletonMap("message",
          "Invalid file name [" + fileName + "], only the following file types are supported (" + StringUtils
              .join(validFileEndings, ", ") + ")."));
    }
  }

  private boolean endsWithOneOf(String toTest, List<String> validEndings) {
      for (String validEnding : validEndings) {
        if (toTest.endsWith(validEnding))
        {
          return true;
        }
      }
      return false;
  }

  protected ModelRepresentation createModelRepresentation(AbstractModel model)
  {
    ModelRepresentation representation;
    if (model.getModelType() != null && model.getModelType() == AbstractModel.MODEL_TYPE_APP)
    {
            representation = new AppDefinitionListModelRepresentation(model);

            AppDefinition appDefinition = null;
            try {
                appDefinition = objectMapper.readValue(model.getModelEditorJson(), AppDefinition.class);
            } catch (Exception e) {
                logger.error("Error deserializing app {}", model.getId(), e);
                throw new InternalServerErrorException("Could not deserialize app definition");
            }
            ((AppDefinitionListModelRepresentation) representation).setAppDefinition(appDefinition);

        } else {
            representation = new ModelRepresentation(model);
        }
        return representation;
    }

    protected String makeValidFilterText(String filterText) {
        String validFilter = null;

        if (filterText != null) {
            String trimmed = StringUtils.trim(filterText);
            if (trimmed.length() >= MIN_FILTER_LENGTH) {
                validFilter = "%" + trimmed.toLowerCase() + "%";
            }
        }
        return validFilter;
    }

}
