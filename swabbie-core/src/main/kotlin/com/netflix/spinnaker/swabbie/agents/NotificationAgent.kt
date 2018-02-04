/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.swabbie.agents

import com.netflix.spinnaker.SwabbieAgent
import com.netflix.spinnaker.swabbie.persistence.LockManager
import com.netflix.spinnaker.swabbie.events.NotifyOwnerEvent
import com.netflix.spinnaker.swabbie.persistence.ResourceTrackingRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class NotificationAgent(
  private val lockManager: LockManager,
  private val resourceTrackingRepository: ResourceTrackingRepository,
  private val applicationEventPublisher: ApplicationEventPublisher,
  private val discoverySupport: DiscoverySupport
): SwabbieAgent {
  private val log: Logger = LoggerFactory.getLogger(javaClass)
  @Scheduled(fixedDelayString = "\${swabbie.notification.frequency.ms:3600000}")
  override fun execute() {
    discoverySupport.ifUP {
      try {
        log.info("Notification Agent Started ...")
        resourceTrackingRepository.getMarkedResourcesToDelete()
          ?.forEach { markedResource ->
            markedResource.takeIf {
              lockManager.acquireLock("{swabbie:notify}:${it.configurationId}", lockTtlSeconds = 3600)
            }?.let {
                applicationEventPublisher.publishEvent(NotifyOwnerEvent(it))
              }
          }
      } catch (e: Exception) {
        log.error("Failed to execute notification agent", e)
      }
    }
  }
}
