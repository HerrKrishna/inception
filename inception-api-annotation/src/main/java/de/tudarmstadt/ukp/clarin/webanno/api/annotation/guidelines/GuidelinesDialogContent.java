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
package de.tudarmstadt.ukp.clarin.webanno.api.annotation.guidelines;

import static org.apache.wicket.markup.html.link.PopupSettings.RESIZABLE;
import static org.apache.wicket.markup.html.link.PopupSettings.SCROLLBARS;

import org.apache.wicket.extensions.ajax.markup.html.modal.ModalWindow;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.PopupSettings;
import org.apache.wicket.markup.html.link.ResourceLink;
import org.apache.wicket.markup.html.list.AbstractItem;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.RepeatingView;
import org.apache.wicket.model.IModel;
import org.apache.wicket.request.resource.ResourceStreamResource;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.apache.wicket.util.resource.FileResourceStream;
import org.apache.wicket.util.resource.IResourceStream;

import de.tudarmstadt.ukp.clarin.webanno.api.ProjectService;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.AnnotatorState;
import de.tudarmstadt.ukp.clarin.webanno.support.lambda.LambdaAjaxLink;

/**
 * Modal window to display annotation guidelines
 */
public class GuidelinesDialogContent
    extends Panel
{
    private static final long serialVersionUID = -2102136855109258306L;

    private @SpringBean ProjectService projectService;

    public GuidelinesDialogContent(String aId, final ModalWindow modalWindow,
            final IModel<AnnotatorState> aModel)
    {
        super(aId);

        // Overall progress by Projects
        RepeatingView guidelineRepeater = new RepeatingView("guidelineRepeater");
        add(guidelineRepeater);

        for (String guidelineFileName : projectService
                .listGuidelines(aModel.getObject().getProject())) {
            AbstractItem item = new AbstractItem(guidelineRepeater.newChildId());

            guidelineRepeater.add(item);

            // Add a popup window link to display annotation guidelines
            PopupSettings popupSettings = new PopupSettings(RESIZABLE | SCROLLBARS).setHeight(500)
                    .setWidth(700);

            IResourceStream stream = new FileResourceStream(projectService
                    .getGuideline(aModel.getObject().getProject(), guidelineFileName));
            ResourceStreamResource resource = new ResourceStreamResource(stream);
            ResourceLink<Void> rlink = new ResourceLink<>("guideine", resource);
            rlink.setPopupSettings(popupSettings);
            item.add(new Label("guidelineName", guidelineFileName));
            item.add(rlink);
        }

        add(new LambdaAjaxLink("cancel", (target) -> modalWindow.close(target)));
    }
}
