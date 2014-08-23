/*
 * Copyright 2014 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.aeron.driver;

import uk.co.real_logic.aeron.common.Agent;
import uk.co.real_logic.aeron.common.concurrent.AtomicArray;
import uk.co.real_logic.aeron.common.concurrent.OneToOneConcurrentArrayQueue;
import uk.co.real_logic.aeron.driver.cmd.ClosePublicationCmd;
import uk.co.real_logic.aeron.driver.cmd.RetransmitPublicationCmd;

import java.util.function.Consumer;

/**
 * Agent that iterates over publications for sending them to registered subscribers.
 */
public class Sender extends Agent
{
    private final AtomicArray<DriverPublication> publications;
    private final OneToOneConcurrentArrayQueue<Object> commandQueue;
    private int roundRobinIndex = 0;

    private Consumer<Object> processConductorCommandsFunc;

    public Sender(final MediaDriver.Context ctx)
    {
        super(ctx.senderIdleStrategy(), ctx.eventLoggerException());

        this.publications = ctx.publications();
        this.commandQueue = ctx.senderCommandQueue();
        processConductorCommandsFunc = this::processConductorCommands;
    }

    public int doWork()
    {
        roundRobinIndex++;
        if (publications.size() < roundRobinIndex)
        {
            roundRobinIndex = 0;
        }

        int workCount = 0;

        workCount += commandQueue.drain(processConductorCommandsFunc);
        workCount += publications.doAction(roundRobinIndex, DriverPublication::send);

        return  workCount;
    }

    private void processConductorCommands(final Object obj)
    {
        if (obj instanceof RetransmitPublicationCmd)
        {
            final RetransmitPublicationCmd cmd = (RetransmitPublicationCmd)obj;
            cmd.publication().onRetransmit(cmd.termId(), cmd.termOffset(), cmd.length());
        }
        else if (obj instanceof ClosePublicationCmd)
        {
            final ClosePublicationCmd cmd = (ClosePublicationCmd)obj;
            cmd.publication().close();
        }
    }
}
