package de.tudarmstadt.ukp.inception.recommendation.util;

import static de.tudarmstadt.ukp.inception.recommendation.api.model.AnnotationSuggestion.FLAG_NO_LABEL;
import static de.tudarmstadt.ukp.inception.recommendation.api.model.AnnotationSuggestion.FLAG_OVERLAP;
import static de.tudarmstadt.ukp.inception.recommendation.api.model.AnnotationSuggestion.FLAG_REJECTED;
import static de.tudarmstadt.ukp.inception.recommendation.api.model.AnnotationSuggestion.FLAG_SKIPPED;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toList;
import static org.apache.uima.fit.util.CasUtil.getAnnotationType;
import static org.apache.uima.fit.util.CasUtil.select;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import javax.persistence.NoResultException;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.fit.util.CasUtil;
import org.apache.uima.fit.util.FSUtil;
import org.apache.uima.fit.util.JCasUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tudarmstadt.ukp.clarin.webanno.api.AnnotationSchemaService;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationFeature;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationLayer;
import de.tudarmstadt.ukp.clarin.webanno.model.Project;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocument;
import de.tudarmstadt.ukp.clarin.webanno.security.model.User;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.inception.recommendation.api.LearningRecordService;
import de.tudarmstadt.ukp.inception.recommendation.api.RecommendationService;
import de.tudarmstadt.ukp.inception.recommendation.api.model.AnnotationSuggestion;
import de.tudarmstadt.ukp.inception.recommendation.api.model.LearningRecord;
import de.tudarmstadt.ukp.inception.recommendation.api.model.Offset;
import de.tudarmstadt.ukp.inception.recommendation.api.model.Predictions;
import de.tudarmstadt.ukp.inception.recommendation.api.model.Recommender;
import de.tudarmstadt.ukp.inception.recommendation.api.model.SuggestionGroup;
import de.tudarmstadt.ukp.inception.recommendation.api.recommender.RecommendationEngine;
import de.tudarmstadt.ukp.inception.recommendation.api.recommender.RecommendationEngineFactory;
import de.tudarmstadt.ukp.inception.recommendation.api.recommender.RecommenderContext;

public class PredictionUtil {
    private static final Logger log = LoggerFactory.getLogger(PredictionUtil.class);
    private static final double NO_SCORE = 0.0;

    public static Predictions getPredictions(SourceDocument document,
            AnnotationSchemaService annoService, RecommendationService recommendationService,
            Optional<CAS> originalCas, Optional<CAS> predictionCas, User user, Project project,
            LearningRecordService learningRecordService)
    {
        Predictions model = null;
        nextLayer: for (AnnotationLayer layer : annoService.listAnnotationLayer(project)) {
            if (!layer.isEnabled()) {
                continue nextLayer;
            }

            List<Recommender> recommenders = recommendationService.getActiveRecommenders(user,
                    layer);

            model = new Predictions(project, user);
            nextRecommender: for (Recommender r : recommenders) {

                // Make sure we have the latest recommender config from the DB - the one from
                // the active recommenders list may be outdated
                Recommender recommender;
                try {
                    recommender = recommendationService.getRecommender(r.getId());
                }
                catch (NoResultException e) {
                    log.info("[{}][{}]: Recommender no longer available... skipping",
                            user.getUsername(), r.getName());
                    continue nextRecommender;
                }

                if (!recommender.isEnabled()) {
                    log.debug("[{}][{}]: Disabled - skipping", user.getUsername(), r.getName());
                    continue nextRecommender;
                }

                RecommenderContext ctx = recommendationService.getContext(user, recommender);

                if (!ctx.isReadyForPrediction()) {
                    log.info("Context for recommender [{}]({}) for user [{}] on document "
                            + "[{}]({}) in project [{}]({}) is not ready yet - skipping recommender",
                            recommender.getName(), recommender.getId(), user.getUsername(),
                            document.getName(), document.getId(), project.getName(),
                            project.getId());
                    continue nextRecommender;
                }

                RecommendationEngineFactory<?> factory = recommendationService
                        .getRecommenderFactory(recommender);

                try {
                    RecommendationEngine recommendationEngine = factory.build(recommender);

                    Type predictionType = getAnnotationType(predictionCas.get(),
                            recommendationEngine.getPredictedType());
                    Feature labelFeature = predictionType
                            .getFeatureByBaseName(recommendationEngine.getPredictedFeature());
                    Optional<Feature> scoreFeature = recommendationEngine.getScoreFeature()
                            .map(predictionType::getFeatureByBaseName);

                    // Remove any annotations that will be predicted (either manually created
                    // or from a previous prediction run) from the CAS
                    removePredictions(predictionCas.get(), predictionType);

                    // Perform the actual prediction
                    recommendationEngine.predict(ctx, predictionCas.get());

                    // Extract the suggestions from the data which the recommender has written
                    // into the CAS
                    List<AnnotationSuggestion> predictions = extractSuggestions(user,
                            predictionCas.get(), predictionType, labelFeature, scoreFeature,
                            document, recommender);

                    // Calculate the visbility of the suggestions. This happens via the original
                    // CAS which contains only the manually created annotations and *not* the
                    // suggestions.
                    Collection<SuggestionGroup> groups = SuggestionGroup.group(predictions);
                    calculateVisibility(learningRecordService, annoService, originalCas.get(),
                            user.getUsername(), layer, groups, 0,
                            originalCas.get().getDocumentText().length());

                    model.putPredictions(layer.getId(), predictions);
                }
                catch (Throwable e) {
                    log.error(
                            "Error applying recommender [{}]({}) for user [{}] to document "
                                    + "[{}]({}) in project [{}]({}) - skipping recommender",
                            recommender.getName(), recommender.getId(), user.getUsername(),
                            document.getName(), document.getId(), project.getName(),
                            project.getId(), e);
                    continue nextRecommender;
                }
            }
        }

        return model;
    }

    private static void removePredictions(CAS aCas, Type aPredictionType)
    {
        for (AnnotationFS fs : CasUtil.select(aCas, aPredictionType)) {
            aCas.removeFsFromIndexes(fs);
        }
    }

    private static List<AnnotationSuggestion> extractSuggestions(User aUser, CAS aCas,
            Type predictionType, Feature predictedFeature, Optional<Feature> aScoreFeature,
            SourceDocument aDocument, Recommender aRecommender)
    {
        int predictionCount = 0;

        List<AnnotationSuggestion> result = new ArrayList<>();
        int id = 0;
        for (AnnotationFS annotationFS : CasUtil.select(aCas, predictionType)) {
            List<Token> tokens = JCasUtil.selectCovered(Token.class, annotationFS);
            Token firstToken = tokens.get(0);
            Token lastToken = tokens.get(tokens.size() - 1);

            String label = annotationFS.getFeatureValueAsString(predictedFeature);
            double score = aScoreFeature.map(f -> FSUtil.getFeature(annotationFS, f, Double.class))
                    .orElse(NO_SCORE);
            String featurename = aRecommender.getFeature().getName();
            String name = aRecommender.getName();

            AnnotationSuggestion ao = new AnnotationSuggestion(id, aRecommender.getId(), name,
                    aRecommender.getLayer().getId(), featurename, aDocument.getName(),
                    firstToken.getBegin(), lastToken.getEnd(), annotationFS.getCoveredText(), label,
                    label, score);

            result.add(ao);
            id++;

            predictionCount++;
        }

        log.debug(
                "[{}]({}) for user [{}] on document "
                        + "[{}]({}) in project [{}]({}) generated {} predictions.",
                aRecommender.getName(), aRecommender.getId(), aUser.getUsername(),
                aDocument.getName(), aDocument.getId(), aRecommender.getProject().getName(),
                aRecommender.getProject().getId(), predictionCount);

        return result;
    }

    /**
     * Goes through all AnnotationObjects and determines the visibility of each one
     */
    public static void calculateVisibility(LearningRecordService aLearningRecordService,
            AnnotationSchemaService aAnnotationService, CAS aCas, String aUser,
            AnnotationLayer aLayer, Collection<SuggestionGroup> aRecommendations, int aWindowBegin,
            int aWindowEnd)
    {
        // Collect all annotations of the given layer within the view window
        Type type = CasUtil.getType(aCas, aLayer.getName());
        List<AnnotationFS> annotationsInWindow = select(aCas, type).stream()
                .filter(fs -> aWindowBegin <= fs.getBegin() && fs.getEnd() <= aWindowEnd)
                .collect(toList());

        // Collect all suggestions of the given layer within the view window
        List<SuggestionGroup> suggestionsInWindow = aRecommendations.stream()
                // Only suggestions for the given layer
                .filter(group -> group.getLayerId() == aLayer.getId())
                // ... and in the given window
                .filter(group -> {
                    Offset offset = group.getOffset();
                    return aWindowBegin <= offset.getBegin() && offset.getEnd() <= aWindowEnd;
                }).collect(toList());

        // Get all the skipped/rejected entries for the current layer
        List<LearningRecord> recordedAnnotations = aLearningRecordService.listRecords(aUser,
                aLayer);

        for (AnnotationFeature feature : aAnnotationService.listAnnotationFeature(aLayer)) {
            Feature feat = type.getFeatureByBaseName(feature.getName());

            // Reduce the annotations to the once which have a non-null feature value. We need to
            // use a multi-valued map here because there may be multiple annotations at a
            // given position.
            MultiValuedMap<Offset, AnnotationFS> annotations = new ArrayListValuedHashMap<>();
            annotationsInWindow.stream().filter(fs -> fs.getFeatureValueAsString(feat) != null)
                    .forEach(fs -> annotations.put(new Offset(fs.getBegin(), fs.getEnd()), fs));
            // We need to constructed a sorted list of the keys for the OverlapIterator below
            List<Offset> sortedAnnotationKeys = new ArrayList<>(annotations.keySet());
            sortedAnnotationKeys
                    .sort(comparingInt(Offset::getBegin).thenComparingInt(Offset::getEnd));

            // Reduce the suggestions to the ones for the given feature. We can use the tree here
            // since we only have a single SuggestionGroup for every position
            Map<Offset, SuggestionGroup> suggestions = new TreeMap<>(
                    comparingInt(Offset::getBegin).thenComparingInt(Offset::getEnd));
            suggestionsInWindow.stream()
                    .filter(group -> group.getFeature().equals(feature.getName()))
                    .forEach(group -> suggestions.put(group.getOffset(), group));

            // If there are no suggestions or no annotations, there is nothing to do here
            if (suggestions.isEmpty() || annotations.isEmpty()) {
                continue;
            }

            // This iterator gives us pairs of annotations and suggestions. Note that bot lists must
            // be sorted in the same way. The suggestion offsets are sorted because they are the
            // keys in a TreeSet - and the annotation offsets are sorted in the same way manually
            OverlapIterator oi = new OverlapIterator(new ArrayList<>(suggestions.keySet()),
                    sortedAnnotationKeys);

            // Bulk-hide any groups that overlap with existing annotations on the current layer
            // and for the current feature
            while (oi.hasNext()) {
                if (oi.getA().overlaps(oi.getB())) {
                    // Fetch the current suggestion and annotation
                    SuggestionGroup group = suggestions.get(oi.getA());
                    for (AnnotationFS annotation : annotations.get(oi.getB())) {
                        String label = annotation.getFeatureValueAsString(feat);
                        for (AnnotationSuggestion suggestion : group) {
                            if (!aLayer.isAllowStacking() || label.equals(suggestion.getLabel())) {
                                suggestion.hide(FLAG_OVERLAP);
                            }
                        }
                    }

                    // Do not want to process the group again since the relevant annotations are
                    // already hidden
                    oi.ignoraA();
                }
                oi.step();
            }

            // Anything that was not hidden so far might still have been rejected or not have a
            // label
            suggestions.values().stream().flatMap(SuggestionGroup::stream)
                    .filter(AnnotationSuggestion::isVisible)
                    .forEach(suggestion -> hideSuggestionsRejectedOrWithoutLabel(suggestion,
                            recordedAnnotations));
        }
    }

    private static void hideSuggestionsRejectedOrWithoutLabel(AnnotationSuggestion aSuggestion,
            List<LearningRecord> aRecordedRecommendations)
    {
        // If there is no label, then hide it
        if (aSuggestion.getLabel() == null) {
            aSuggestion.hide(FLAG_NO_LABEL);
            return;
        }

        // If it was rejected or skipped, it hide it
        for (LearningRecord record : aRecordedRecommendations) {
            if (record.getOffsetCharacterBegin() == aSuggestion.getBegin()
                    && record.getOffsetCharacterEnd() == aSuggestion.getEnd()
                    && record.getAnnotation().equals(aSuggestion.getLabel())) {
                switch (record.getUserAction()) {
                case REJECTED:
                    aSuggestion.hide(FLAG_REJECTED);
                    break;
                case SKIPPED:
                    aSuggestion.hide(FLAG_SKIPPED);
                    break;
                default:
                    // Nothing to do for the other cases. ACCEPTED annotation are filtered out
                    // because the overlap with a created annotation and the same for CORRECTED
                }
                return;
            }
        }
    }

}
