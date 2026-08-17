(function () {
  "use strict";

  function controller() {
    if (!window.ResearchWorkbench) {
      throw new Error("Research workbench is not ready");
    }
    return window.ResearchWorkbench;
  }

  async function activate() {
    const workbench = controller();
    workbench.setLibraryKind("card-pack");
    return workbench.search(workbench.state.query);
  }

  async function search(query = "") {
    const workbench = controller();
    workbench.setLibraryKind("card-pack");
    return workbench.search(query);
  }

  async function openCardPack(packId) {
    const workbench = controller();
    workbench.setLibraryKind("card-pack");
    return workbench.openCardPack(Number(packId));
  }

  window.ResearchCatalogCenter = {
    get state() {
      return window.ResearchWorkbench?.state || null;
    },
    activate,
    search,
    openCardPack,
  };
}());
