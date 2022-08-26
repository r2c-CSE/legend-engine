//  Copyright 2022 Goldman Sachs
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.

package org.finos.legend.engine.external.format.xml;

import org.eclipse.collections.api.list.MutableList;
import org.finos.legend.engine.external.format.xml.read.IXmlDeserializeExecutionNodeSpecifics;
import org.finos.legend.engine.external.format.xml.read.XmlReader;
import org.finos.legend.engine.external.shared.runtime.ExternalFormatRuntimeExtension;
import org.finos.legend.engine.external.shared.runtime.read.ExecutionHelper;
import org.finos.legend.engine.plan.execution.nodes.ExecutionNodeExecutor;
import org.finos.legend.engine.plan.execution.nodes.helpers.platform.ExecutionNodeJavaPlatformHelper;
import org.finos.legend.engine.plan.execution.nodes.helpers.platform.JavaHelper;
import org.finos.legend.engine.plan.execution.nodes.state.ExecutionState;
import org.finos.legend.engine.plan.execution.result.Result;
import org.finos.legend.engine.plan.execution.result.object.StreamingObjectResult;
import org.finos.legend.engine.protocol.pure.v1.model.executionPlan.nodes.JavaPlatformImplementation;
import org.finos.legend.engine.protocol.pure.v1.model.executionPlan.nodes.externalFormat.ExternalFormatInternalizeExecutionNode;
import org.pac4j.core.profile.CommonProfile;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;

public class XsdRuntimeExtension implements ExternalFormatRuntimeExtension
{
    private static final String CONTENT_TYPE = "application/xml";

    @Override
    public List<String> getContentTypes()
    {
        return Collections.singletonList(CONTENT_TYPE);
    }

    @Override
    public Result executeInternalizeExecutionNode(ExternalFormatInternalizeExecutionNode node, MutableList<CommonProfile> profiles, ExecutionState executionState)
    {
        try
        {
            String specificsClassName = JavaHelper.getExecutionClassFullName((JavaPlatformImplementation) node.implementation);
            Class<?> specificsClass = ExecutionNodeJavaPlatformHelper.getClassToExecute(node, specificsClassName, executionState, profiles);
            IXmlDeserializeExecutionNodeSpecifics specifics = (IXmlDeserializeExecutionNodeSpecifics) specificsClass.getConstructor().newInstance();

            InputStream stream = ExecutionHelper.inputStreamFromResult(node.executionNodes().getFirst().accept(new ExecutionNodeExecutor(profiles, new ExecutionState(executionState))));
            String location = ExecutionHelper.locationFromSourceNode(node.executionNodes().getFirst());
            XmlReader<?> deserializer = new XmlReader(specifics, stream, location);
            return new StreamingObjectResult<>(deserializer.startStream());
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }
}