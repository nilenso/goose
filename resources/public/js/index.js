window.onload = () => {
    const purge_dialog = document.querySelector(".purge-dialog");
    const showDialog = document.querySelector(".purge-dialog-show")
    const cancelDialog = document.querySelector("dialog .cancel")
    showDialog.addEventListener("click", () => {
        purge_dialog.showModal();
    })
    cancelDialog.addEventListener("click", () => {
        purge_dialog.close();
    })
}
