window.onload = () => {

	const possibleTypesBasedOnJobType = {
		"scheduled": ["scheduled", "failed"],
		"enqueued": ["unexecuted", "failed"]
	}
	let isPolling = false;
	let pollInterval = 2;
	let livePollTimer;

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

	function addListeners() {
		attachSelectAllCheckboxEventListener();
		attachPurgeDialogEventListener();
		attachFilterTypeEventListener();
		attachDeleteDialogEventListener();
		attachCheckboxListeners();
		attachWhenCheckboxEventListener();
		attachThemeToggleEventListener();
		attachPollingEventListener();
	}

	function saveTheme(theme) {
		document.body.className = theme;
		document.body.style.display = 'block';
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

	function saveState() {
		localStorage.setItem('goosePollingInterval', pollInterval.toString());
		localStorage.setItem('gooseLivePoll', isPolling.toString());
	}

	function updateButtonDisplay() {
		const pollButton = document.getElementById('pollButton');
		const stopButton = document.getElementById('stopButton');

		if (isPolling) {
			pollButton.style.display = 'none';
			stopButton.style.display = 'inline-block';
		} else {
			pollButton.style.display = 'inline-block';
			stopButton.style.display = 'none';
		}
	}

	function startPolling() {
		isPolling = true;
		updateButtonDisplay();
		saveState();
		scheduleLivePoll();
	}

	function stopPolling() {
		isPolling = false;
		updateButtonDisplay();
		saveState();
		clearTimeout(livePollTimer);
	}

	function checkResponse(response) {
		if (!response.ok) {
			throw new Error(`HTTP error! status: ${response.status}`);
		}
		return response;
	}

	function replacePage(html) {
		const parser = new DOMParser();
		const doc = parser.parseFromString(html, 'text/html');
		const page = doc.querySelector('#page')
		document.querySelector("#page").replaceWith(page)
		addListeners()
	}

	function showError(error) {
		console.error('Polling error:', error);
		errorElement.textContent = `Error: ${error.message}`;
	}

	function livePollCallback() {
		clearTimeout(livePollTimer);

		fetch(window.location.href)
			.then(checkResponse)
			.then(resp => resp.text())
			.then(replacePage)
			.catch(showError)
			.finally(scheduleLivePoll);
	}

	function scheduleLivePoll() {
		if (isPolling) {
			livePollTimer = setTimeout(livePollCallback, pollInterval * 1000);
		}
	}

	function loadState() {
		const savedInterval = localStorage.getItem('goosePollingInterval');
		const savedPollingState = localStorage.getItem('gooseLivePoll');

		const intervalSlider = document.getElementById('intervalSlider');
		const intervalValue = document.getElementById('intervalValue');

		if (savedInterval && intervalSlider && intervalValue) {
			pollInterval = parseInt(savedInterval);
			intervalSlider.value = pollInterval;
			intervalValue.textContent = pollInterval;
		}

		if (savedPollingState === 'true') {
			isPolling = true;
			updateButtonDisplay();
			scheduleLivePoll();
		}
	}

	function attachPollingEventListener() {
		const intervalSlider = document.getElementById('intervalSlider');
		const pollButton = document.getElementById('pollButton');
		const stopButton = document.getElementById('stopButton');

		if (pollButton && stopButton) {
			pollButton.addEventListener('click', startPolling);
			stopButton.addEventListener('click', stopPolling);
		}

		if (intervalSlider) {
			intervalSlider.addEventListener('input', function () {
				pollInterval = parseInt(this.value);
				intervalValue.textContent = pollInterval;
				saveState();
				if (isPolling) {
					clearTimeout(livePollTimer);
					scheduleLivePoll();
				}
			});
		}
	}
	addListeners();
	setTheme();
	loadState();
}
