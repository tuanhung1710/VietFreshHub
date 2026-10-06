package VietFreshHub.Product.controller;
import VietFreshHub.Product.dto.*;
import VietFreshHub.Product.exception.CatalogException;
import VietFreshHub.Product.service.ProductService;
import VietFreshHub.Product.service.impl.ProductServiceImpl;
import VietFreshHub.Inventory.service.InventoryRules;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.security.core.Authentication;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.List;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
@Controller @RequiredArgsConstructor @RequestMapping("/seller/products")
public class ProductController {
    private final ProductService products;
    @GetMapping("/{id}/variants/{variant}/prices")
    public String prices(Authentication a,@PathVariable Long id,@PathVariable Long variant,@RequestParam(defaultValue="1") int page,Model m) {
        m.addAttribute("events",products.priceHistory(a,id,variant,page));m.addAttribute("productId",id);m.addAttribute("variantId",variant);return "product/price-history";
    }
    @GetMapping
    public String list(Authentication a,@RequestParam(defaultValue="") String q,@RequestParam(required=false) Long category,
        @RequestParam(defaultValue="") String status,@RequestParam(defaultValue="false") boolean archived,
        @RequestParam(defaultValue="") String business,@RequestParam(defaultValue="") String approval,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate createdFrom,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate createdTo,
        @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int size,Model m) {
        m.addAttribute("rows",products.search(a,q,category,status,archived,"",business,approval,createdFrom,createdTo,page,size));m.addAttribute("categories",products.categories());
        m.addAttribute("catalogSummary",products.catalogSummary(a));
        m.addAttribute("q",q);m.addAttribute("category",category);m.addAttribute("status",status);m.addAttribute("archived",archived);
        m.addAttribute("business",business);m.addAttribute("approval",approval);m.addAttribute("createdFrom",createdFrom);m.addAttribute("createdTo",createdTo);
        return "product/list";
    }
    private String form(Model m,Long id) {
        m.addAttribute("productId",id);m.addAttribute("categories",products.categories());m.addAttribute("units",ProductServiceImpl.UNITS);m.addAttribute("today",InventoryRules.today());return "product/form";
    }
    @GetMapping("/new") public String createForm(Model m) {
        ProductRequest f=new ProductRequest();f.getVariants().add(new VariantRequest());m.addAttribute("form",f);return form(m,null);
    }
    @PostMapping public String create(Authentication a,@Valid @ModelAttribute("form") ProductRequest f,BindingResult errors,Model m,RedirectAttributes flash) {
        if(errors.hasErrors())return form(m,null);
        try{Long id=products.create(a,f);flash.addFlashAttribute("success","Đã tạo sản phẩm và SKU. Sản phẩm đang chờ Admin duyệt.");return "redirect:/seller/products/"+id;}
        catch(CatalogException e){errors.reject("business",e.getMessage());return form(m,null);}
    }
    @GetMapping("/{id}") public String detail(Authentication a,@PathVariable Long id,Model m) {
        m.addAttribute("row",products.get(a,id));return "product/detail";
    }
    @GetMapping("/{id}/edit") public String edit(Authentication a,@PathVariable Long id,Model m) {
        m.addAttribute("form",products.editForm(a,id));return form(m,id);
    }
    @PostMapping("/{id}/edit") public String update(Authentication a,@PathVariable Long id,@Valid @ModelAttribute("form") ProductRequest f,BindingResult errors,Model m,RedirectAttributes flash) {
        if(errors.hasErrors())return form(m,id);
        try{products.update(a,id,f);flash.addFlashAttribute("success","Đã lưu thông tin và gửi lại để duyệt.");return "redirect:/seller/products/"+id;}
        catch(CatalogException e){errors.reject("business",e.getMessage());return form(m,id);}
    }
    @PostMapping("/{id}/status") public String status(Authentication a,@PathVariable Long id,@Valid @ModelAttribute ActionRequest f,BindingResult errors,RedirectAttributes flash) {
        if(errors.hasErrors())flash.addFlashAttribute("error","Chọn trạng thái và nhập lý do tối đa 900 ký tự.");
        else try{products.changeStatus(a,id,f);flash.addFlashAttribute("success","Đã cập nhật trạng thái sản phẩm.");}catch(CatalogException e){flash.addFlashAttribute("error",e.getMessage());}
        return "redirect:/seller/products/"+id;
    }
    private String variantForm(Authentication a,Model m,Long product,Long variant) {
        m.addAttribute("row",products.get(a,product));m.addAttribute("productId",product);m.addAttribute("variantId",variant);m.addAttribute("units",ProductServiceImpl.UNITS);m.addAttribute("today",InventoryRules.today());return "product/variant-form";
    }
    @GetMapping("/{id}/variants/new") public String add(Authentication a,@PathVariable Long id,Model m) {
        m.addAttribute("form",new VariantRequest());return variantForm(a,m,id,null);
    }
    @GetMapping("/{id}/variants/{variant}/edit") public String editVariant(Authentication a,@PathVariable Long id,@PathVariable Long variant,Model m) {
        m.addAttribute("form",products.variantForm(a,id,variant));return variantForm(a,m,id,variant);
    }
    @PostMapping("/{id}/variants") public String add(Authentication a,@PathVariable Long id,@Valid @ModelAttribute("form") VariantRequest f,BindingResult errors,Model m,RedirectAttributes flash) {
        if(errors.hasErrors())return variantForm(a,m,id,null);
        try{products.addVariant(a,id,f);flash.addFlashAttribute("success","Đã thêm SKU. Sản phẩm đã duyệt sẽ được gửi duyệt lại.");return "redirect:/seller/products/"+id;}
        catch(CatalogException e){errors.reject("business",e.getMessage());return variantForm(a,m,id,null);}
    }
    @PostMapping("/{id}/variants/{variant}/edit") public String editVariant(Authentication a,@PathVariable Long id,@PathVariable Long variant,@Valid @ModelAttribute("form") VariantRequest f,BindingResult errors,Model m,RedirectAttributes flash) {
        if(errors.hasErrors())return variantForm(a,m,id,variant);
        try{products.updateVariant(a,id,variant,f);flash.addFlashAttribute("success","Đã lưu SKU. Thay đổi cấu trúc sẽ gửi sản phẩm đã duyệt về chờ duyệt.");return "redirect:/seller/products/"+id;}
        catch(CatalogException e){errors.reject("business",e.getMessage());return variantForm(a,m,id,variant);}
    }
    @PostMapping("/{id}/variants/{variant}/status") public String variantStatus(Authentication a,@PathVariable Long id,@PathVariable Long variant,@Valid @ModelAttribute VariantActionRequest f,BindingResult errors,RedirectAttributes flash) {
        if(errors.hasErrors())flash.addFlashAttribute("error","Nhập trạng thái và lý do.");
        else try{products.changeVariantStatus(a,id,variant,f);flash.addFlashAttribute("success","Đã cập nhật trạng thái SKU.");}catch(CatalogException e){flash.addFlashAttribute("error",e.getMessage());}
        return "redirect:/seller/products/"+id;
    }
}
