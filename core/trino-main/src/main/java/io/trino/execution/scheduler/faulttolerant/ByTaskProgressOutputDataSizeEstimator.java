/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.execution.scheduler.faulttolerant;

import com.google.common.primitives.ImmutableLongArray;
import io.trino.Session;
import io.trino.execution.StageId;
import io.trino.execution.scheduler.OutputDataSizeEstimate;
import io.trino.execution.scheduler.faulttolerant.EventDrivenFaultTolerantQueryScheduler.StageExecution;

import java.util.Optional;
import java.util.function.Function;

import static io.trino.SystemSessionProperties.getFaultTolerantExecutionMinSourceStageProgress;

public class ByTaskProgressOutputDataSizeEstimator
        implements OutputDataSizeEstimator
{
    public static class Factory
            implements OutputDataSizeEstimatorFactory
    {
        @Override
        public OutputDataSizeEstimator create(Session session)
        {
            return new ByTaskProgressOutputDataSizeEstimator(getFaultTolerantExecutionMinSourceStageProgress(session));
        }
    }

    private final double minSourceStageProgress;

    private ByTaskProgressOutputDataSizeEstimator(double minSourceStageProgress)
    {
        this.minSourceStageProgress = minSourceStageProgress;
    }

    @Override
    public Optional<OutputDataSizeEstimateResult> getEstimatedOutputDataSize(StageExecution stageExecution, Function<StageId, StageExecution> stageExecutionLookup, boolean parentEager)
    {
        if (!stageExecution.isNoMorePartitions()) {
            return Optional.empty();
        }

        int allPartitionsCount = stageExecution.getPartitionsCount();
        int remainingPartitionsCount = stageExecution.getRemainingPartitionsCount();

        if (remainingPartitionsCount == allPartitionsCount) {
            return Optional.empty();
        }

        double progress = (double) (allPartitionsCount - remainingPartitionsCount) / allPartitionsCount;

        if (progress < minSourceStageProgress) {
            return Optional.empty();
        }

        long[] currentOutputDataSize = stageExecution.currentOutputDataSize();

        ImmutableLongArray.Builder estimateBuilder = ImmutableLongArray.builder(currentOutputDataSize.length);

        for (long partitionSize : currentOutputDataSize) {
            estimateBuilder.add((long) (partitionSize / progress));
        }
        return Optional.of(new OutputDataSizeEstimateResult(new OutputDataSizeEstimate(estimateBuilder.build()), OutputDataSizeEstimateStatus.ESTIMATED_BY_PROGRESS));
    }
}
