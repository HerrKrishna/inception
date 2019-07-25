/*
 * Copyright 2019
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.inception.recommendation.sidebar;

import static de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.WebAnnoCasUtil.getDocumentTitle;
import static de.tudarmstadt.ukp.inception.recommendation.api.model.AnnotationSuggestion.FLAG_TRANSIENT_ACCEPTED;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.uima.cas.CAS;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.protocol.ws.api.WebSocketBehavior;
import org.apache.wicket.protocol.ws.api.WebSocketRequestHandler;
import org.apache.wicket.protocol.ws.api.message.ConnectedMessage;
import org.apache.wicket.protocol.ws.api.message.IWebSocketPushMessage;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wicketstuff.event.annotation.OnEvent;

import de.tudarmstadt.ukp.clarin.webanno.api.AnnotationSchemaService;
import de.tudarmstadt.ukp.clarin.webanno.api.DocumentService;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.exception.AnnotationException;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.AnnotatorState;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.page.AnnotationPageBase;
import de.tudarmstadt.ukp.clarin.webanno.api.dao.CasMetadataUtils;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationFeature;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationLayer;
import de.tudarmstadt.ukp.clarin.webanno.security.UserDao;
import de.tudarmstadt.ukp.clarin.webanno.security.model.User;
import de.tudarmstadt.ukp.clarin.webanno.support.lambda.LambdaAjaxLink;
import de.tudarmstadt.ukp.inception.recommendation.api.RecommendationService;
import de.tudarmstadt.ukp.inception.recommendation.api.evaluation.EvaluationResult;
import de.tudarmstadt.ukp.inception.recommendation.api.model.AnnotationSuggestion;
import de.tudarmstadt.ukp.inception.recommendation.api.model.EvaluatedRecommender;
import de.tudarmstadt.ukp.inception.recommendation.api.model.Predictions;
import de.tudarmstadt.ukp.inception.recommendation.api.model.Recommender;
import de.tudarmstadt.ukp.inception.recommendation.event.PredictionsSwitchedEvent;
import de.tudarmstadt.ukp.inception.recommendation.event.RecommenderTaskEvent;

public class RecommenderInfoPanel
    extends Panel
{
    private static final long serialVersionUID = -5921076859026638039L;

    private @SpringBean RecommendationService recommendationService;
    private @SpringBean UserDao userService;
    private @SpringBean AnnotationSchemaService annotationService;
    private @SpringBean DocumentService documentService;
    
    private final Logger log = LoggerFactory.getLogger(getClass());
    
    private WebMarkupContainer resultsContainer;
    private ListView<Recommender> recommenderGroups; 
    
    public RecommenderInfoPanel(String aId, IModel<AnnotatorState> aModel)
    {
        super(aId, aModel);
        setOutputMarkupId(true);
        
        WebMarkupContainer mainContainer = new WebMarkupContainer("mainContainer");
        mainContainer.setOutputMarkupId(true);
        add(mainContainer);
        
        recommenderGroups = new ListView<Recommender>("recommender")
        {
            private static final long serialVersionUID = -631500052426449048L;

            @Override
            protected void populateItem(ListItem<Recommender> item)
            {
                User user = getPanelModelObject().getUser();
                Recommender recommender = item.getModelObject();
                List<EvaluatedRecommender> activeRecommenders = recommendationService
                        .getActiveRecommenders(user, recommender.getLayer());
                Optional<EvaluationResult> evalResult = activeRecommenders.stream()
                        .filter(r -> r.getRecommender().equals(recommender))
                        .map(EvaluatedRecommender::getEvaluationResult)
                        .findAny();
                item.add(new Label("name", recommender.getName()));
                item.add(new Label("state", evalResult.isPresent() ? "active" : "off"));

                item.add(new LambdaAjaxLink("acceptAll", _target -> 
                        actionAcceptAll(_target, recommender)));
                
                resultsContainer = createResultsContainer(evalResult);
                item.add(resultsContainer);
            }
        };
        
        recommenderGroups.setModel(LoadableDetachableModel.of(() -> recommendationService
                .listEnabledRecommenders(aModel.getObject().getProject())));
        recommenderGroups.setOutputMarkupId(true);
        mainContainer.add(recommenderGroups);
        
        add(new WebSocketBehavior() {

            private static final long serialVersionUID = 1L;

            @Override
            protected void onConnect(ConnectedMessage aMessage)
            {
                super.onConnect(aMessage);
                log.info(String.format("User with sessionID %s connected.",
                        aMessage.getSessionId()));
            }

            @Override
            protected void onPush(WebSocketRequestHandler aHandler, IWebSocketPushMessage aMessage)
            {
                log.info(String.format("Received event: %s", aMessage.toString()));
                if (aMessage instanceof RecommenderTaskEvent) {
                    aHandler.add(mainContainer);
                }
            }
        });
    }
        
    private WebMarkupContainer createResultsContainer(
            Optional<EvaluationResult> evalResult)
    {
        WebMarkupContainer resultsContainer = new WebMarkupContainer("resultsContainer");
        resultsContainer.setVisible(evalResult.isPresent());
        resultsContainer.add(new Label("f1Score",
                evalResult.map(EvaluationResult::computeF1Score).orElse(0.0d)));
        resultsContainer.add(new Label("accuracy",
                evalResult.map(EvaluationResult::computeAccuracyScore).orElse(0.0d)));
        resultsContainer.add(new Label("precision",
                evalResult.map(EvaluationResult::computePrecisionScore).orElse(0.0d)));
        resultsContainer.add(new Label("recall",
                evalResult.map(EvaluationResult::computeRecallScore).orElse(0.0d)));
        
        return resultsContainer;
    }
    
    public AnnotatorState getPanelModelObject()
    {
        return (AnnotatorState) getDefaultModelObject();
    }
    
    @OnEvent
    public void onRenderAnnotations(PredictionsSwitchedEvent aEvent)
    {
        aEvent.getRequestHandler().add(this);
    }
    
    private void actionAcceptAll(AjaxRequestTarget aTarget, Recommender aRecommender)
        throws AnnotationException, IOException
    {
        AnnotatorState state = getPanelModelObject();
        User user = state.getUser();
        
        AnnotationPageBase page = findParent(AnnotationPageBase.class);
        
        CAS cas = page.getEditorCas();
     
        Predictions predictions = recommendationService.getPredictions(user, state.getProject());

        // TODO #176 use the document Id once it it available in the CAS
        String sourceDocumentName = CasMetadataUtils.getSourceDocumentName(cas)
                .orElse(getDocumentTitle(cas));
        
        // Extract all predictions for the current document / recommender
        List<AnnotationSuggestion> suggestions = predictions.getPredictions().entrySet().stream()
                .filter(f -> f.getKey().getDocumentName().equals(sourceDocumentName))
                .filter(f -> f.getKey().getRecommenderId() == aRecommender.getId().longValue())
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());

        for (AnnotationSuggestion suggestion : suggestions) {
            // Upsert an annotation based on the suggestion
            AnnotationLayer layer = annotationService.getLayer(suggestion.getLayerId());
            AnnotationFeature feature = annotationService.getFeature(suggestion.getFeature(),
                    layer);
            recommendationService.upsertFeature(annotationService,
                    state.getDocument(), user.getUsername(), cas, layer, feature,
                    suggestion.getLabel(), suggestion.getBegin(), suggestion.getEnd());
    
            // Hide the suggestion. This is faster than having to recalculate the visibility status
            // for the entire document or even for the part visible on screen.
            suggestion.hide(FLAG_TRANSIENT_ACCEPTED);
        }
        
        // Save CAS after annotations have been created
        page.writeEditorCas(cas);
        
        page.actionRefreshDocument(aTarget);
    }
}