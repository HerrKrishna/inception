/*
 * Licensed to the Technische Universität Darmstadt under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The Technische Universität Darmstadt 
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.
 *  
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.inception.recommendation.api.recommender;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.Parameterized;

import de.tudarmstadt.ukp.inception.recommendation.api.evaluation.IncrementalSplitter;

@RunWith(Enclosed.class)
public class IncrementalSplitterTest
{

    @RunWith(Parameterized.class)
    public static class ParameterizedTests
    {
        private final double k;
        private final int trainingSetSize;
        private final int testSetSize;

        public ParameterizedTests(double aK, int aTrainingSetSize, int aTestSetSize)
        {
            k = aK;
            trainingSetSize = aTrainingSetSize;
            testSetSize = aTestSetSize;
        }

        @Test
        public void thatSplittingWorks()
        {
            List<String> data = asList("0", "1", "2", "3", "4", "5", "6", "7", "8", "9");
            IncrementalSplitter splitter = new IncrementalSplitter(k, 1, 4);

            int currentTrainingSetSize = 1;

            List<String> trainingSet = new ArrayList<>();
            List<String> testSet = new ArrayList<>();
            while (splitter.hasNext()) {
                splitter.next();

                trainingSet.clear();
                testSet.clear();

                for (String s : data) {
                    switch (splitter.getTargetSet(s)) {
                    case TRAIN:
                        trainingSet.add(s);
                        break;
                    case TEST:
                        testSet.add(s);
                        break;
                    default:
                        // Do nothing
                        break;
                    }
                }

                assertThat(trainingSet).as("Training set has correct size")
                        .hasSize(currentTrainingSetSize);
                assertThat(testSet).as("Test set has correct size").hasSize(testSetSize);

                currentTrainingSetSize++;
            }

            assertThat(trainingSet).as("Training set has correct size").hasSize(trainingSetSize);
            assertThat(testSet).as("Test set has correct size").hasSize(testSetSize);
        }

        @Parameterized.Parameters
        public static Collection<Object[]> data()
        {
            // k, trainingSetSize, testSetSize
            return Arrays.asList(new Object[][] { //
                    { 0.1, 2, 8 }, //
                    { 0.2, 2, 8 }, //
                    { 0.3, 3, 7 }, //
                    { 0.4, 4, 6 }, //
                    { 0.5, 5, 5 }, //
                    { 0.6, 6, 4 }, //
                    { 0.7, 7, 3 }, //
                    { 0.8, 8, 2 }, //
                    { 0.9, 8, 2 } });
        }
    }

    @RunWith(JUnit4.class)
    public static class NonParameterizedTests
    {
        @Test(expected = IllegalArgumentException.class)
        public void thatPercentageHasToBePercentage()
        {
            IncrementalSplitter splitter = new IncrementalSplitter(42.1337, 1, 10);
        }
    }
}
