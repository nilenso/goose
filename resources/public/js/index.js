window.onload = () => {

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

  function createFilterValueInputElement() {
    const input = document.createElement("input");
    input.setAttribute('type', 'text');
    input.setAttribute('name', 'filter-value');
    input.setAttribute('class', 'filter-value');
    input.setAttribute('placeholder', 'filter value')
    input.setAttribute('value', '');
    return input;
  }
  function createFilterValueSelectElement() {
    const select = document.createElement('select');
    select.setAttribute('id', 'job-type-select')
    select.setAttribute('name', 'filter-value')
    select.setAttribute('class', 'filter-value')
    const options = ["unexecuted", "failed"];
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
    const INPUT_FILTER_TYPES = ["id", "execute-fn-sym"];
    const filterTypeSelect = document.querySelector(".filter-type");

    filterTypeSelect.addEventListener("change", (event) => {
      const filterValuesDiv = document.querySelector(".filter-values");
      const selectedFilterType = filterTypeSelect.value;
      const currentFilterValueElement = document.querySelector(".filter-opts-items .filter-value");

      let newFilterValueElement;

      switch (true) {
        case SELECT_FILTER_TYPES.includes(selectedFilterType):
          newFilterValueElement = createFilterValueSelectElement();
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

  attachPurgeDialogEventListener();
  attachFilterTypeEventListener();
}
