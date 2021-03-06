package org.jboss.as.console.client.shared.help;

import com.allen_sauer.gwt.log.client.Log;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.HTML;
import org.jboss.as.console.client.shared.dispatch.DispatchAsync;
import org.jboss.as.console.client.shared.dispatch.impl.DMRAction;
import org.jboss.as.console.client.shared.dispatch.impl.DMRResponse;
import org.jboss.as.console.client.shared.runtime.charts.Column;
import org.jboss.as.console.client.widgets.forms.ApplicationMetaData;
import org.jboss.as.console.client.widgets.forms.BeanMetaData;
import org.jboss.as.console.client.widgets.forms.PropertyBinding;
import org.jboss.ballroom.client.widgets.forms.FormAdapter;
import org.jboss.dmr.client.ModelNode;
import org.jboss.dmr.client.Property;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static org.jboss.dmr.client.ModelDescriptionConstants.*;

/**
 * @author Heiko Braun
 * @date 6/8/11
 */
public class HelpSystem {

    private DispatchAsync dispatcher;
    private ApplicationMetaData propertyMetaData;

    @Inject
    public HelpSystem(DispatchAsync dispatcher, ApplicationMetaData propertyMetaData) {
        this.dispatcher = dispatcher;
        this.propertyMetaData = propertyMetaData;
    }

    public void getAttributeDescriptions(
            ModelNode resourceAddress,
            final FormAdapter form,
            final AsyncCallback<HTML> callback)
    {


        final ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_RESOURCE_DESCRIPTION_OPERATION);
        operation.get(ADDRESS).set(resourceAddress);

        // build field name list

        List<String> formItemNames = form.getFormItemNames();
        BeanMetaData beanMetaData = propertyMetaData.getBeanMetaData(form.getConversionType());
        List<PropertyBinding> bindings = beanMetaData.getProperties();
        final List<String> fieldNames = new ArrayList<String>();

        for(PropertyBinding binding : bindings)
        {
            if(formItemNames.contains(binding.getJavaName())) {
                String[] detypedPath = binding.getDetypedName().split("/");
                fieldNames.add(detypedPath[0]);
            }
        }

        dispatcher.execute(new DMRAction(operation), new AsyncCallback<DMRResponse>() {
            @Override
            public void onSuccess(DMRResponse result) {
                ModelNode response = result.get();

                if (response.get(OUTCOME).asString().equals("success")
                        && response.hasDefined(RESULT)) {

                    final SafeHtmlBuilder html = new SafeHtmlBuilder();
                    html.appendHtmlConstant("<table class='help-attribute-descriptions'>");

                    List<ModelNode> modelNodes = response.get(RESULT).asList();
                    List<String> processed = new ArrayList<String>();

                    for (ModelNode res : modelNodes) {
                        matchAttributes(processed, res, fieldNames, html);
                        matchChildren(processed, res, fieldNames, html);
                    }

                    html.appendHtmlConstant("</table>");
                    callback.onSuccess(new HTML(html.toSafeHtml()));

                } else {
                    System.out.println(operation);
                    System.out.println(response);
                    onFailure(new Exception(""));
                }
            }

            @Override
            public void onFailure(Throwable caught) {
                callback.onFailure(caught);
            }
        });
    }

    public interface AddressCallback
    {
        ModelNode getAddress();
    }

    public void getMetricDescriptions(
                AddressCallback address,
               Column[] columns,
               final AsyncCallback<HTML> callback)
       {

           final List<String> attributeNames = new LinkedList<String>();
           for(Column c : columns)
               attributeNames.add(c.getDeytpedName());

           final ModelNode operation = address.getAddress();
           operation.get(OP).set(READ_RESOURCE_DESCRIPTION_OPERATION);

           dispatcher.execute(new DMRAction(operation), new AsyncCallback<DMRResponse>() {
               @Override
               public void onSuccess(DMRResponse result) {
                   ModelNode response = result.get();

                   if (response.get(OUTCOME).asString().equals("success")
                           && response.hasDefined(RESULT)) {

                       final SafeHtmlBuilder html = new SafeHtmlBuilder();
                       html.appendHtmlConstant("<table class='help-attribute-descriptions'>");

                       List<ModelNode> modelNodes = response.get(RESULT).asList();
                       List<String> processed = new ArrayList<String>();

                       for (ModelNode res : modelNodes) {
                           matchAttributes(processed, res, attributeNames, html);
                           matchChildren(processed, res, attributeNames, html);
                       }

                       html.appendHtmlConstant("</table>");
                       callback.onSuccess(new HTML(html.toSafeHtml()));

                   } else {
                       System.out.println(operation);
                       System.out.println(response);
                       onFailure(new Exception(""));
                   }
               }

               @Override
               public void onFailure(Throwable caught) {
                   callback.onFailure(caught);
               }
           });
       }


    private void matchAttributes(List<String> processed, ModelNode prototype, List<String> fieldNames, SafeHtmlBuilder html) {
        matchSubElement(processed, prototype, fieldNames, html, ATTRIBUTES);
    }

    private void matchChildren(List<String> processed, ModelNode prototype, List<String> fieldNames, SafeHtmlBuilder html) {
        matchSubElement(processed, prototype, fieldNames, html, CHILDREN);
    }

    private void matchSubElement(List<String> processed, ModelNode prototype, List<String> fieldNames, SafeHtmlBuilder html, String entity) {
        if (prototype.hasDefined(RESULT))
            prototype = prototype.get(RESULT).asObject();

        if (!prototype.hasDefined(entity))
            return;

        try {

            List<Property> attributes = prototype.get(entity).asPropertyList();

            for(Property prop : attributes)
            {
                String childName = prop.getName();
                ModelNode value = prop.getValue();

                if(fieldNames.contains(childName))
                {
                    // TODO: Workaround AS7-3426
                    if(processed.contains(childName))
                        continue;
                    else
                        processed.add(childName);

                    html.appendHtmlConstant("<tr class='help-field-row'>");
                    html.appendHtmlConstant("<td class='help-field-name'>");
                    html.appendEscaped(childName).appendEscaped(": ");
                    html.appendHtmlConstant("</td>");
                    html.appendHtmlConstant("<td class='help-field-desc'>");
                    html.appendEscaped(value.get("description").asString());
                    html.appendHtmlConstant("</td>");
                    html.appendHtmlConstant("</tr>");
                }
            }
        } catch (IllegalArgumentException e) {
             Log.error("Failed to read help description", e);
        }
    }
}
