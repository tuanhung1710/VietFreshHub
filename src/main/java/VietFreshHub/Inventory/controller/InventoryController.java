package VietFreshHub.Inventory.controller;
import VietFreshHub.Product.service.ProductService;
import VietFreshHub.Product.dto.ProductResponse;
import VietFreshHub.Product.dto.ProductResponse.VariantResponse;
import VietFreshHub.Product.exception.CatalogException;
import VietFreshHub.Inventory.dto.*;
import VietFreshHub.Inventory.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.security.core.Authentication;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
@Controller @RequiredArgsConstructor @RequestMapping("/seller/inventory")
public class InventoryController {
    private final InventoryService inventory;
    private final ProductService products;
    @GetMapping public String list(Authentication a,@RequestParam(defaultValue="") String q,@RequestParam(required=false) Long category,
        @RequestParam(defaultValue="") String stock,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int size,Model m) {
        m.addAttribute("rows",products.search(a,q,category,"",false,stock,page,size));m.addAttribute("categories",products.categories());
        m.addAttribute("catalogSummary",products.catalogSummary(a));
        m.addAttribute("q",q);m.addAttribute("category",category);m.addAttribute("stock",stock);m.addAttribute("today",InventoryRules.today());return "inventory/list";
    }
    private String restockView(Authentication a,Long product,Long variant,Model m) {
        ProductResponse row=products.get(a,product);
        m.addAttribute("variant",row.variants().stream().filter(v->v.variant().getVariantId().equals(variant)).findFirst().orElseThrow(()->new org.springframework.security.access.AccessDeniedException("SKU không thuộc sản phẩm.")));
        m.addAttribute("row",row);m.addAttribute("productId",product);m.addAttribute("variantId",variant);m.addAttribute("today",InventoryRules.today());return "inventory/restock";
    }
    @GetMapping("/products/{product}/variants/{variant}/restock") public String restockForm(Authentication a,@PathVariable Long product,@PathVariable Long variant,Model m) {
        BatchRequest f=new BatchRequest();f.setReceivedDate(InventoryRules.today());m.addAttribute("form",f);return restockView(a,product,variant,m);
    }
    @PostMapping("/products/{product}/variants/{variant}/restock") public String restock(Authentication a,@PathVariable Long product,@PathVariable Long variant,@Valid @ModelAttribute("form") BatchRequest f,BindingResult errors,Model m,RedirectAttributes flash) {
        restockView(a,product,variant,m);
        if(errors.hasErrors())return "inventory/restock";
        try{inventory.restock(a,variant,f);flash.addFlashAttribute("success","Đã nhập lô mới và ghi nhật ký.");return "redirect:/seller/inventory";}
        catch(CatalogException e){errors.reject("business",e.getMessage());return "inventory/restock";}
    }
    private String batchView(Authentication a,Long id,String action,Model m) {
        m.addAttribute("row",inventory.batch(a,id));m.addAttribute("batchId",id);m.addAttribute("action",action);m.addAttribute("today",InventoryRules.today());return "inventory/batch-form";
    }
    @GetMapping("/batches/{id}/{action}") public String batchForm(Authentication a,@PathVariable Long id,@PathVariable String action,Model m) {
        InventoryRules.require(List.of("edit","adjust","dispose","markdown").contains(action),"Thao tác không hợp lệ.");
        Object f=switch(action){case "edit"->inventory.batchForm(a,id);case "markdown"->{MarkdownRequest r=new MarkdownRequest();r.setEndDate(inventory.batch(a,id).batch().getExpiryDate());yield r;}default->new StockChangeRequest();};
        m.addAttribute("form",f);return batchView(a,id,action,m);
    }
    @PostMapping("/batches/{id}/edit") public String edit(Authentication a,@PathVariable Long id,@Valid @ModelAttribute("form") BatchRequest f,BindingResult errors,Model m,RedirectAttributes flash) {
        if(errors.hasErrors())return batchView(a,id,"edit",m);
        try{inventory.updateBatch(a,id,f);flash.addFlashAttribute("success","Đã cập nhật thông tin lô; số lượng giữ nguyên.");return "redirect:/seller/inventory";}
        catch(CatalogException e){errors.reject("business",e.getMessage());return batchView(a,id,"edit",m);}
    }
    @PostMapping("/batches/{id}/adjust") public String adjust(Authentication a,@PathVariable Long id,@Valid @ModelAttribute("form") StockChangeRequest f,BindingResult errors,Model m,RedirectAttributes flash) {
        if(errors.hasErrors())return batchView(a,id,"adjust",m);
        try{inventory.adjust(a,id,f);flash.addFlashAttribute("success","Đã điều chỉnh kho.");return "redirect:/seller/inventory";}
        catch(CatalogException e){errors.reject("business",e.getMessage());return batchView(a,id,"adjust",m);}
    }
    @PostMapping("/batches/{id}/dispose") public String dispose(Authentication a,@PathVariable Long id,@Valid @ModelAttribute("form") StockChangeRequest f,BindingResult errors,Model m,RedirectAttributes flash) {
        if(errors.hasErrors())return batchView(a,id,"dispose",m);
        try{inventory.dispose(a,id,f);flash.addFlashAttribute("success","Đã ghi nhận hủy hàng hết hạn.");return "redirect:/seller/inventory";}
        catch(CatalogException e){errors.reject("business",e.getMessage());return batchView(a,id,"dispose",m);}
    }
    @PostMapping("/batches/{id}/markdown") public String markdown(Authentication a,@PathVariable Long id,@Valid @ModelAttribute("form") MarkdownRequest f,BindingResult errors,Model m,RedirectAttributes flash) {
        if(errors.hasErrors())return batchView(a,id,"markdown",m);
        try{inventory.markdown(a,id,f);flash.addFlashAttribute("success","Đã tạo giá cận hạn cho lô.");return "redirect:/seller/inventory";}
        catch(CatalogException e){errors.reject("business",e.getMessage());return batchView(a,id,"markdown",m);}
    }
    @PostMapping("/batches/{id}/block") public String block(Authentication a,@PathVariable Long id,@RequestParam boolean blocked,@RequestParam String reason,RedirectAttributes flash) {
        try{inventory.block(a,id,blocked,reason);flash.addFlashAttribute("success","Đã cập nhật khóa lô.");}catch(CatalogException e){flash.addFlashAttribute("error",e.getMessage());}return "redirect:/seller/inventory";
    }
    @PostMapping("/batches/{id}/cancel-markdown") public String cancel(Authentication a,@PathVariable Long id,@RequestParam String reason,RedirectAttributes flash) {
        try{inventory.cancelMarkdown(a,id,reason);flash.addFlashAttribute("success","Đã dừng giá cận hạn.");}catch(CatalogException e){flash.addFlashAttribute("error",e.getMessage());}return "redirect:/seller/inventory";
    }
    @GetMapping("/journal") public String journal(Authentication a,@RequestParam(required=false) Long variant,@RequestParam(defaultValue="") String type,
        @RequestParam(defaultValue="") String q,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="5") int size,Model m) {
        m.addAttribute("events",inventory.journal(a,variant,type,q,from,to,page,size));m.addAttribute("variant",variant);m.addAttribute("type",type);m.addAttribute("q",q);m.addAttribute("from",from);m.addAttribute("to",to);return "inventory/journal";
    }
    @GetMapping("/audit") public String audit(Authentication a,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int size,Model m) {
        m.addAttribute("events",inventory.audit(a,page,size));return "inventory/audit";
    }
    @GetMapping("/batches/{id}/label") public String label(Authentication a,@PathVariable Long id,Model m) {
        m.addAttribute("row",inventory.batch(a,id));return "inventory/label";
    }
}
