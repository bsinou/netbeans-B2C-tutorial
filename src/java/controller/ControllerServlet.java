package controller;

import cart.ShoppingCart;
import entity.Category;
import entity.Product;
import java.io.IOException;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import javax.ejb.EJB;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import session.CategoryFacade;
import session.OrderManager;
import session.ProductFacade;
import validate.Validator;

/**
 * Main servlet for the AffableBean Demo Application
 */
@WebServlet(name = "ControllerServlet",
        loadOnStartup = 1, urlPatterns = {"/category",
            "/viewCart", "/checkout", "/chooseLanguage",
            "/updateCart", "/addToCart", "/purchase"})
public class ControllerServlet extends HttpServlet {

    private String surcharge;

    @EJB
    private CategoryFacade categoryFacade;
    @EJB
    private ProductFacade productFacade;
    @EJB
    private OrderManager orderManager;

    @Override
    public void init(ServletConfig servletConfig) throws ServletException {
        super.init(servletConfig);
        // initialize servlet with configuration information
        surcharge = servletConfig.getServletContext().getInitParameter("deliverySurcharge");
        // store category list in servlet context
        getServletContext().setAttribute("categories", categoryFacade.findAll());
    }

    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String userPath = request.getServletPath();
        HttpSession session = request.getSession();

        if (userPath.equals("/category")) {
            // get categoryId from request
            String categoryId = request.getQueryString();
            if (categoryId != null) {
                Category selectedCategory = categoryFacade.find(Short.parseShort(categoryId));
                session.setAttribute("selectedCategory", selectedCategory);
                Collection<Product> categoryProducts = selectedCategory.getProductCollection();
                session.setAttribute("categoryProducts", categoryProducts);
            }

        } else if (userPath.equals("/viewCart")) {
            String clear = request.getParameter("clear");
            if ((clear != null) && clear.equals("true")) {
                ShoppingCart cart = (ShoppingCart) session.getAttribute("cart");
                cart.clear();
            }
            userPath = "/cart";

        } else if (userPath.equals("/checkout")) {
            ShoppingCart cart = (ShoppingCart) session.getAttribute("cart");
            // calculate total
            cart.calculateTotal(surcharge);
            // forward to checkout page and switch to a secure channel

        } else if (userPath.equals("/chooseLanguage")) {
            // get language choice
            String language = request.getParameter("language");
            // place in request scope
            request.setAttribute("language", language);
            String userView = (String) session.getAttribute("view");
            if ((userView != null)
                    && (!userView.equals("/index"))) {     // index.jsp exists outside 'view' folder
                // so must be forwarded separately
                userPath = userView;
            } else {
                // if previous view is index or cannot be determined, send user to welcome page
                try {
                    request.getRequestDispatcher("/index.jsp").forward(request, response);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                return;
            }
        }
        doForward(request, response, userPath);
    }

    /**
     * Handles the HTTP <code>POST</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // ensures that user input is interpreted as
        // 8-bit Unicode (e.g., for Czech characters)
        request.setCharacterEncoding("UTF-8");

        String userPath = request.getServletPath();
        HttpSession session = request.getSession();
        ShoppingCart cart = (ShoppingCart) session.getAttribute("cart");
        Validator validator = new Validator();

        // if addToCart action is called
        if (userPath.equals("/addToCart")) {
            // if user is adding item to cart for first time
            // create cart object and attach it to user session
            if (cart == null) {
                cart = new ShoppingCart();
                session.setAttribute("cart", cart);
            }
            // get user input from request
            String productId = request.getParameter("productId");
            if (!productId.isEmpty()) {
                Product product = productFacade.find(Integer.parseInt(productId));
                cart.addItem(product);
            }
            userPath = "/category";

        } else if (userPath.equals("/updateCart")) {
            String productId = request.getParameter("productId");
            String quantity = request.getParameter("quantity");

            boolean invalidEntry = validator.validateQuantity(productId, quantity);
            if (!invalidEntry) {
                Product product = productFacade.find(Integer.parseInt(productId));
                cart.update(product, quantity);
            }
            userPath = "/cart";
        } else if (userPath.equals("/purchase")) {
            if (cart != null) {
                // extract user data from request
                String name = request.getParameter("name");
                String email = request.getParameter("email");
                String phone = request.getParameter("phone");
                String address = request.getParameter("address");
                String cityRegion = request.getParameter("cityRegion");
                String ccNumber = request.getParameter("creditcard");

                // validate user data
                boolean validationErrorFlag = false;
                validationErrorFlag = validator.validateForm(name, email, phone, address, cityRegion, ccNumber, request);
                // if validation error found, return user to checkout
                if (validationErrorFlag == true) {
                    request.setAttribute("validationErrorFlag", validationErrorFlag);
                    userPath = "/checkout";
                    // otherwise, save order to database
                } else {
                    int orderId = orderManager.placeOrder(name, email, phone, address, cityRegion, ccNumber, cart);
                    if (orderId != 0) {

                        // in case language was set using toggle, get language choice before destroying session
                        Locale locale = (Locale) session.getAttribute("javax.servlet.jsp.jstl.fmt.locale.session");
                        String language = (locale != null)?(String) locale.getLanguage():"";
                        
                        // dissociate shopping cart from session
                        cart = null;
                        // end session
                        session.invalidate();
                        if (!language.isEmpty())                        
                            request.setAttribute("language", language);  

                        // get order details
                        Map orderMap = orderManager.getOrderDetails(orderId);
                        // place order details in request scope
                        request.setAttribute("customer", orderMap.get("customer"));
                        request.setAttribute("products", orderMap.get("products"));
                        request.setAttribute("orderRecord", orderMap.get("orderRecord"));
                        request.setAttribute("orderedProducts", orderMap.get("orderedProducts"));
                        userPath = "/confirmation";

                    } else {
                        // otherwise, send back to checkout page and display error
                        userPath = "/checkout";
                        request.setAttribute("orderFailureFlag", true);
                    }
                }
            }
        }
        doForward(request, response, userPath);
    }

    private void doForward(HttpServletRequest request, HttpServletResponse response, String userPath) {
        // use RequestDispatcher to forward request internally
        String url = "/WEB-INF/view" + userPath + ".jsp";

        try {
            request.getRequestDispatcher(url).forward(request, response);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>
}
