window.onload = () => {

  const possibleTypesBasedOnJobType = {"scheduled": ["scheduled", "failed"],
   "enqueued": ["unexecuted", "failed"]}

  function attachPurgeDialogEventListener() {
    const purgeDialog = document.querySelector(".purge-dialog");
    const showPurgeDialog = document.querySelector(".purge-dialog-show")
    const cancelPurgeDialog = document.querySelector(".purge-dialog .cancel")
    if (purgeDialog) {
      showPurgeDialog.addEventListener("click", (event) => {
        purgeDialog.showModal()
      })
      cancelPurgeDialog.addEventListener("click", (event) => {
        purgeDialog.close()
      })
    }
  }

  function attachDeleteDialogEventListener() {
    const deleteDialog = document.querySelector(".delete-dialog")
    const showDeleteDialogBtn = document.querySelector(".delete-dialog-show")
    const cancelDeleteDialogBtn = document.querySelector(".delete-dialog .cancel")
    if (deleteDialog) {
      showDeleteDialogBtn.addEventListener("click", (event) => {
        deleteDialog.showModal()
      })
      cancelDeleteDialogBtn.addEventListener("click", (event) => {
        deleteDialog.close()
      })
    }
  }

  function createFilterValueInputElement() {
    const input = document.createElement("input");
    input.setAttribute('type', 'text');
    input.setAttribute('required', 'true');
    input.setAttribute('name', 'filter-value');
    input.setAttribute('class', 'filter-value');
    input.setAttribute('placeholder', 'filter value')
    input.setAttribute('value', '');
    return input;
  }

  function createFilterValueSelectElement(jobType) {
    const select = document.createElement('select');
    select.setAttribute('id', 'job-type-select')
    select.setAttribute('required', 'true');
    select.setAttribute('name', 'filter-value')
    select.setAttribute('class', 'filter-value')

    const options = possibleTypesBasedOnJobType[jobType]

    options.forEach(function (t) {
      var option = document.createElement('option');
      option.value = t;
      option.textContent = t;
      select.appendChild(option);
    });
    return select;
  }

  function attachFilterTypeEventListener() {
    const SELECT_FILTER_TYPES = ["type"];
    const INPUT_FILTER_TYPES = ["id", "execute-fn-sym", "queue"];
    const filterTypeSelect = document.querySelector(".filter-type");
    if (filterTypeSelect) {
      filterTypeSelect.addEventListener("change", (event) => {
        const filterValuesDiv = document.querySelector(".filter-values");
        const selectedFilterType = filterTypeSelect.value;
        const currentFilterValueElement = document.querySelector(".filter-opts-items .filter-value");
        const jobType = document.getElementById("job-type").value

        let newFilterValueElement;

        switch (true) {
          case SELECT_FILTER_TYPES.includes(selectedFilterType):
            newFilterValueElement = createFilterValueSelectElement(jobType);
            break;
          case INPUT_FILTER_TYPES.includes(selectedFilterType):
            newFilterValueElement = createFilterValueInputElement();
            break;
        }

        if (newFilterValueElement) {
          currentFilterValueElement.remove();
          filterValuesDiv.appendChild(newFilterValueElement);
        }
      });
    }
  }

  function toggleActionButtonsVisibility() {
    const actionButtons = document.querySelectorAll('.actions input');
    const checkboxes = document.querySelectorAll('.checkbox');
    const checkedBoxesCount = Array.from(checkboxes).filter(c => c.checked).length;

    actionButtons.forEach((button) => {
      if (checkedBoxesCount > 0) {
        button.removeAttribute("disabled");
      } else {
        button.setAttribute("disabled", "");
      }
    });
  }

  function attachCheckboxListeners() {
    const checkboxes = document.querySelectorAll('.checkbox');

    if (checkboxes) {
      checkboxes.forEach((checkbox) => {
        checkbox.addEventListener('change', toggleActionButtonsVisibility)
      })
    }
  }

  function attachSelectAllCheckboxEventListener() {
    const headerCheckbox = document.getElementById('checkbox-h');
    const checkboxes = document.querySelectorAll('.checkbox');
    if (headerCheckbox) {
      headerCheckbox.addEventListener('change', function () {
        checkboxes.forEach(function (checkbox) {
          checkbox.checked = headerCheckbox.checked;
          // To trigger visibility of action buttons
          const event = new Event('change');
          checkbox.dispatchEvent(event);
        });
      });
    }
  }

  function attachWhenCheckboxEventListener() {
    const whenOptionCheckbox = document.getElementById('when-option');
    if (whenOptionCheckbox) {
      whenOptionCheckbox.addEventListener('change', function () {
        const isTimeFormatAbsolute = whenOptionCheckbox.checked
        const scheduleRunAtRelTimeDivs = document.getElementsByClassName('schedule-run-at-rel-time')
        const scheduleRunAtAbsTimeDivs = document.getElementsByClassName('schedule-run-at-abs-time')

        console.log(whenOptionCheckbox.checked)
        console.log("scheduleRunAtRelTimeDiv", scheduleRunAtRelTimeDivs)
        console.log("scheduleRunAtRelTimeDiv", scheduleRunAtAbsTimeDivs)
        if (isTimeFormatAbsolute) {
          for (let div of scheduleRunAtRelTimeDivs) {
            div.classList.add('invisible');
          }
          for (let div of scheduleRunAtAbsTimeDivs) {
            div.classList.remove('invisible');
          }
        } else {
          for (let div of scheduleRunAtAbsTimeDivs) {
            div.classList.add('invisible');
          }
          for (let div of scheduleRunAtRelTimeDivs) {
            div.classList.remove('invisible');
          }
        }
      });
    }
  }

function saveTheme(theme) {
  document.body.className = theme;
  document.body.style.display = 'block';
  console.log("Theme");
  const themeToggle = document.getElementById('isThemeDark');
  themeToggle.checked = theme === 'dark';
  localStorage.setItem("theme", theme);
}

function setTheme(isDark = null) {
  const theme = isDark === null
    ? localStorage.getItem("theme") || 'light'
    : isDark ? 'dark' : 'light';

  saveTheme(theme);
}

function attachThemeToggleEventListener() {
  const themeToggle = document.getElementById('isThemeDark');
  themeToggle.addEventListener('change', (event) => setTheme(event.target.checked));
}

  attachSelectAllCheckboxEventListener();
  attachPurgeDialogEventListener();
  attachFilterTypeEventListener();
  attachDeleteDialogEventListener();
  attachCheckboxListeners();
  attachWhenCheckboxEventListener();
  attachThemeToggleEventListener();
  setTheme();
}
