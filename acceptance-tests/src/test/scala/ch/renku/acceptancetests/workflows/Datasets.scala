/*
 * Copyright 2023 Swiss Data Science Center (SDSC)
 * A partnership between École Polytechnique Fédérale de Lausanne (EPFL) and
 * Eidgenössische Technische Hochschule Zürich (ETHZ).
 *
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

package ch.renku.acceptancetests.workflows

import ch.renku.acceptancetests.model.datasets
import ch.renku.acceptancetests.model.datasets.{DatasetName, FileUrl}
import ch.renku.acceptancetests.pages.DatasetPage
import ch.renku.acceptancetests.tooling.{AcceptanceSpec, KnowledgeGraphApi}
import org.openqa.selenium.{StaleElementReferenceException, WebElement}

import scala.concurrent.duration._
import scala.util.Try

trait Datasets {
  self: AcceptanceSpec with Project with KnowledgeGraphApi =>

  def `verify dataset was created`(datasetName: DatasetName): Unit = `try few times with page reload` { _ =>
    `navigate to the dataset`(DatasetPage(datasetName))
  }

  def `navigate to project info`: Unit = `try few times with page reload` { _ =>
    Given("the user is on the Datasets card")
    click on projectPage.Datasets.goBackButton sleep (10 second)
  }

  def `create a dataset`(datasetName: DatasetName): DatasetPage = {
    import Modification._
    val newDatasetName = datasets.DatasetName("new")
    val newDatasetPage = DatasetPage(newDatasetName)

    `try few times with page reload` { _ =>
      Given("the user is on the Datasets tab")
      click on projectPage.Datasets.tab sleep (10 second)
    }

    `try few times with page reload` { _ =>
      When("the user clicks on the Add Dataset button")
      click on projectPage.Datasets.addDatasetButton sleep (5 seconds)
    }
    verify that newDatasetPage.ModificationForm.formTitle contains "Add Dataset"

    And(s"the user add the title '$datasetName' to the new dataset")
    `changing its title`(to = datasetName.toString).modifying(newDatasetPage) sleep (1 second)

    And("the user saves the changes")
    newDatasetPage.ModificationForm.clickOnDatasetSubmitButton sleep (2 second)
    pause asLongAsBrowserAt newDatasetPage

    And("all the events are processed by the knowledge-graph ")
    `wait for KG to process events`(projectPage.asProjectIdentifier, webDriver)

    Then("the user should see its newly created dataset")
    val datasetPage = DatasetPage(datasetName)
    reloadPage() sleep (2 seconds)

    `try few times with page reload` { _ =>
      verify browserAt datasetPage
      verify that datasetPage.datasetTitle contains datasetName.value
    }

    datasetPage
  }

  def `create a dataset with uploaded file`(datasetName: DatasetName, fileUrl: FileUrl): DatasetPage = {
    import Modification._
    val newDatasetName = datasets.DatasetName("new")
    val newDatasetPage = DatasetPage(newDatasetName)

    `try few times with page reload` { _ =>
      Given("the user is on the Datasets tab")
      click on projectPage.Datasets.tab sleep (1 second)
    }

    When("the user clicks on the Add Dataset button")
    `try few times with page reload` { _ =>
      click on projectPage.Datasets.addDatasetButton sleep (1 second)
    }
    verify that newDatasetPage.ModificationForm.formTitle contains "Add Dataset"

    And(s"the user add the title '$datasetName' to the new dataset")
    `changing its title`(to = datasetName.toString).modifying(newDatasetPage) sleep (1 second)

    And(s"the user sets a file upload url")
    `adding file upload url`(to = fileUrl.toString).modifying(newDatasetPage) sleep (1 second)

    And(s"the user uploads the file")
    click on newDatasetPage.ModificationForm.fileUploadButton sleep (3 second)

    And("the user saves the changes")
    newDatasetPage.ModificationForm.clickOnDatasetSubmitButton sleep (2 second)
    pause asLongAsBrowserAt newDatasetPage

    And("all the events are processed by the knowledge-graph")
    `wait for KG to process events`(projectPage.asProjectIdentifier, webDriver)

    val datasetPage = DatasetPage(datasetName)
    Then("the user should see its newly created dataset")
    reloadPage() sleep (2 seconds)

    `try few times with page reload` { _ =>
      verify browserAt datasetPage
      verify that datasetPage.datasetTitle contains datasetName.value
    }

    datasetPage
  }

  def `navigate to the dataset`(datasetPage: DatasetPage): Unit = {

    `try few times with page reload` { _ =>
      Given("the user is on the Datasets tab")
      click on projectPage.Datasets.tab sleep (5 seconds)
      verify userCanSee projectPage.Datasets.addDatasetButton
    }

    When(s"the user clicks on the dataset name")
    click on projectPage.Datasets.DatasetsList
      .maybeLink(to = datasetPage)
      .getOrElse(logAndFail(s"Cannot find '${datasetPage.datasetName}' Dataset among project datasets"))

    sleep(5 second)

    Then(s"the user should see the dataset details")
    verify browserAt datasetPage
  }

  def `modify the dataset`(datasetPage: DatasetPage, by: Modification, and: Modification*): Unit = {
    Given(s"the user is on the page of the dataset")
    `navigate to the dataset`(datasetPage)

    When("the user clicks on the modify button")
    `try few times with page reload` { _ =>
      click on datasetPage.modifyButton sleep (3 seconds)
    }
    verify userCanSee datasetPage.ModificationForm.formTitle

    And(s"the user modifies the dataset by ${by.name}")
    by.modifying(datasetPage)
    and.toList.foreach { by =>
      And(s"by ${by.name}")
      by.modifying(datasetPage)
    }

    And("the user saves the modification")
    datasetPage.ModificationForm.clickOnDatasetSubmitButton sleep (2 seconds)

    verify browserAt datasetPage

    And("all the events are processed by the knowledge-graph")
    `wait for KG to process events`(projectPage.asProjectIdentifier, webDriver)

    And("the user should see its newly created dataset")
    reloadPage() sleep (2 seconds)

    val newTitle = by.newValue
    Then(s"the dataset new title should contain '$newTitle'")
    verify that datasetPage.datasetTitle contains newTitle
  }

  case class Modification private (name: String, field: DatasetPage => WebElement, newValue: String) {
    def modifying(datasetPage: DatasetPage): Unit = Try {
      field(datasetPage) enterValue newValue
    } fold (retryOnStaleElement(datasetPage), identity)

    private def retryOnStaleElement(datasetPage: DatasetPage): Throwable => Unit = {
      case _: StaleElementReferenceException => modifying(datasetPage)
      case other => throw other
    }
  }

  object Modification {

    def `changing its title`(to: String): Modification =
      Modification("changing its title", datasetPage => datasetPage.ModificationForm.datasetTitleField, to)

    def `adding file upload url`(to: String): Modification =
      Modification("uploading a file", datasetPage => datasetPage.ModificationForm.fileUploadField, to)

    def `changing its description`(to: String): Modification =
      Modification("changing its description", datasetPage => datasetPage.ModificationForm.datasetDescriptionField, to)
  }
}
