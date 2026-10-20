(() => {
    const provinceSelect = document.getElementById("provinceSelect");
    const wardSelect = document.getElementById("wardSelect");
    const provinceName = document.getElementById("provinceName");
    const wardName = document.getElementById("wardName");
    const errorElement = document.getElementById("administrativeAddressError");

    if (!provinceSelect || !wardSelect || !provinceName || !wardName) return;

    const apiBase = "https://provinces.open-api.vn/api/v2";
    const initialProvinceName = provinceSelect.dataset.initialName || "";
    const initialWardName = wardSelect.dataset.initialName || "";
    const initialProvinceId = provinceSelect.dataset.initialId || "";
    const initialWardId = wardSelect.dataset.initialId || "";

    function showError(message) {
        errorElement.textContent = message;
        errorElement.classList.remove("hidden");
    }

    function clearError() {
        errorElement.textContent = "";
        errorElement.classList.add("hidden");
    }

    function setPlaceholder(select, label) {
        select.replaceChildren(new Option(label, ""));
    }

    async function fetchJson(url) {
        const response = await fetch(url, { headers: { Accept: "application/json" } });
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        return response.json();
    }

    function findByIdOrName(items, id, name) {
        if (id) {
            const byId = items.find((item) => String(item.code) === String(id));
            if (byId) return byId;
        }
        return items.find((item) => item.name === name);
    }

    async function loadWards(provinceId, selectedWardId = "", selectedWardName = "") {
        wardSelect.disabled = true;
        wardName.value = "";
        setPlaceholder(wardSelect, "Đang tải phường/xã...");
        if (!provinceId) {
            setPlaceholder(wardSelect, "Chọn tỉnh/thành phố trước");
            return;
        }

        try {
            const province = await fetchJson(`${apiBase}/p/${encodeURIComponent(provinceId)}?depth=2`);
            const wards = Array.isArray(province.wards) ? province.wards : [];
            setPlaceholder(wardSelect, "Chọn phường/xã");
            for (const ward of wards) {
                const option = new Option(ward.name, ward.code);
                option.dataset.name = ward.name;
                wardSelect.add(option);
            }
            wardSelect.disabled = false;

            const selectedWard = findByIdOrName(wards, selectedWardId, selectedWardName);
            if (selectedWard) {
                wardSelect.value = String(selectedWard.code);
                wardName.value = selectedWard.name;
            }
        } catch (error) {
            setPlaceholder(wardSelect, "Không tải được phường/xã");
            showError("Không tải được danh sách phường/xã. Vui lòng thử chọn lại tỉnh/thành phố.");
        }
    }

    provinceSelect.addEventListener("change", async () => {
        clearError();
        const selectedProvince = provinceSelect.selectedOptions[0];
        provinceName.value = selectedProvince?.dataset.name || "";
        wardSelect.value = "";
        wardName.value = "";
        await loadWards(provinceSelect.value);
    });

    wardSelect.addEventListener("change", () => {
        const selectedWard = wardSelect.selectedOptions[0];
        wardName.value = selectedWard?.dataset.name || "";
        clearError();
    });

    document.getElementById("addressForm")?.addEventListener("submit", (event) => {
        const provinceOption = provinceSelect.selectedOptions[0];
        const wardOption = wardSelect.selectedOptions[0];
        provinceName.value = provinceOption?.dataset.name || "";
        wardName.value = wardOption?.dataset.name || "";
        if (!provinceName.value || !wardName.value) {
            event.preventDefault();
            showError("Vui lòng chọn tỉnh/thành phố và phường/xã.");
        }
    });

    async function initialize() {
        try {
            const provinces = await fetchJson(`${apiBase}/?depth=1`);
            setPlaceholder(provinceSelect, "Chọn tỉnh/thành phố");
            for (const province of provinces) {
                const option = new Option(province.name, province.code);
                option.dataset.name = province.name;
                provinceSelect.add(option);
            }

            const selectedProvince = findByIdOrName(provinces, initialProvinceId, initialProvinceName);
            if (selectedProvince) {
                provinceSelect.value = String(selectedProvince.code);
                provinceName.value = selectedProvince.name;
                await loadWards(selectedProvince.code, initialWardId, initialWardName);
            } else {
                provinceName.value = "";
                setPlaceholder(wardSelect, "Chọn tỉnh/thành phố trước");
            }
        } catch (error) {
            setPlaceholder(provinceSelect, "Không tải được tỉnh/thành phố");
            setPlaceholder(wardSelect, "Chọn tỉnh/thành phố trước");
            showError("Không tải được danh sách tỉnh/thành phố. Vui lòng tải lại trang để thử lại.");
        }
    }

    initialize();
})();
