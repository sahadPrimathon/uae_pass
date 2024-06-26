//
//  UAEPASSWVConteroller.swift
//  UaePassDemo
//
//  Created by Mohammed Gomaa on 17/02/2021.
//  Copyright © 2021 Mohammed Gomaa. All rights reserved.
//

import UIKit
import WebKit

@available(iOS 13.0, *)
@objc public class UAEPassWebViewController: UIViewController, WKNavigationDelegate, WKUIDelegate {
    
    @objc public var urlString: String!
    @objc public var onUAEPassSigningCodeRecieved:(() -> Void)? = nil
    @objc public var onUAEPassSuccessBlock: ((String) -> Void)? = nil
    @objc public var onUAEPassFailureBlock: ((String) -> Void)? = nil
    @objc public var onSigningCompleted: ((String) -> Void)? = nil
    @objc public var onDismiss: (() -> Void)? = nil
    @objc var webView: WKWebView?
    var successURLR: String?
    var failureURLR: String?
    public var isSigning: Bool? = false
    public var skipDismiss = false
    public var alreadyCanceled = false
    public override func viewDidLoad() {
        self.title = "UAE PASS"
        contentMode.preferredContentMode = .mobile
        if #available(iOS 14.0, *) {
            contentMode.allowsContentJavaScript = true
        }
        self.navigationController?.setNavigationBarHidden(false, animated: false)
    }
    
    let contentMode = WKWebpagePreferences.init()
    
    public func reloadwithURL(url: String) {
        webView = UAEPASSRouter.shared.webView
        webView?.navigationDelegate = self
        webView?.uiDelegate = self
        webView?.frame = self.view.frame
        if let webView = webView {
            _ = view.addSubviewStretched(subview: webView)
        }
        self.urlString = url
        restoreCookies()

        if let url = URL(string: url) {
            var urlRequest = URLRequest(url: url)
            urlRequest.timeoutInterval = 30
            webView?.load(urlRequest)
        }
    }
    
    public override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        if isBeingDismissed && !skipDismiss {
            onDismiss?()
        }
    }
    
    public override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        let cancelVw = UIView()
        cancelVw.frame = CGRect(x: 0, y: 0, width: Int(self.view.bounds.width), height: 40)
        let closeLbl = UILabel()
        closeLbl.textColor = .black
        closeLbl.frame = CGRect(x: 0, y:cancelVw.frame.height/2 - 8, width: view.frame.width, height: 20)
        closeLbl.textAlignment = .center
        closeLbl.text = "Swipe to Close"
        cancelVw.addSubview(closeLbl)
        view.addSubview(cancelVw)
    }
    
    @objc public func forceReload() {
        if let successurl = successURLR {
            webView?.load(URLRequest(url: URL(string: successurl)!))
        } else {
            webView?.reload()
        }
    }
    
    @objc public func forceStop() {
        webView?.stopLoading()
        if alreadyCanceled == false {
            skipDismiss = true
            alreadyCanceled = true
            onUAEPassFailureBlock?("reject")
        }
    }
    
    public func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, preferences: WKWebpagePreferences, decisionHandler: @escaping (WKNavigationActionPolicy, WKWebpagePreferences) -> Void) {
        let url = navigationAction.request.url
        guard let urlString = navigationAction.request.mainDocumentURL?.absoluteString else { return }
        print(urlString)
        
        if urlString.contains("error=access_denied") {
            if alreadyCanceled == false {
                skipDismiss = true
                alreadyCanceled = true
                onUAEPassFailureBlock?("reject")
            }
            decisionHandler(.cancel, contentMode)
        } 
        else if urlString.contains("error=cancel") {
            if alreadyCanceled == false {
                skipDismiss = true
                alreadyCanceled = true
                onUAEPassFailureBlock?("cancel")
            }
            decisionHandler(.cancel, contentMode)
        }
        else if urlString.contains(UAEPASSRouter.shared.spConfig.redirectUriLogin) && urlString.contains("code=") {
            if let url = url, let token = url.valueOf("code") {
                
                if onUAEPassSuccessBlock != nil && !token.isEmpty {
                    storeCookies()
                    skipDismiss = true
                    onUAEPassSuccessBlock?(token)
                }
            }
            decisionHandler(.cancel, contentMode)
        } else if urlString.contains("uaepass://")  {
            // isUAEPassOpened = true
            let newURLString = urlString.replacingOccurrences(of: "uaepass://", with: UAEPASSRouter.shared.environmentConfig.uaePassSchemeURL)
            successURLR = navigationAction.request.mainDocumentURL?.valueOf("successurl")
            failureURLR = navigationAction.request.mainDocumentURL?.valueOf("failureurl")
            let listItems = newURLString.components(separatedBy: "successurl")
            if listItems.count > 0 {
                if let customScheme = listItems.first {
                    let successScheme = HandleURLScheme.externalURLSchemeSuccess()
                    let failureScheme = HandleURLScheme.externalURLSchemeFail()
                    let urlScheme = "\(customScheme)successurl=\(successScheme)&failureurl=\(failureScheme)&closeondone=true"
                    print("urlScheme: \(urlScheme)")
                    if let url = URL(string: urlScheme),  UIApplication.shared.canOpenURL(url) {
                        HandleURLScheme.openCustomApp(fullUrl: urlScheme)
                    }
                }
            }
            decisionHandler(.cancel, contentMode)
            //        } else if (urlString.contains("csrf_token=") && urlString.contains("show_signing_details=")) {
            //            onSigningCompleted?(urlString)
            //            decisionHandler(.allow, contentMode)
        } else if urlString.contains("status=") {
            if urlString.contains("status=success") {
                decisionHandler(.allow, contentMode)
            } else if (urlString.contains("status=finished") && urlString.contains("signer_process_id")) {
                onSigningCompleted?(urlString)
                decisionHandler(.allow, contentMode)
            } else {
                onUAEPassFailureBlock?("Signing Failed")
                decisionHandler(.cancel, contentMode)
            }
        } else if navigationAction.navigationType == .linkActivated && (urlString.contains("signup") || urlString.contains("account-recovery")) {
            if let url = navigationAction.request.mainDocumentURL {
                UIApplication.shared.open(url, options: [:], completionHandler: nil)
                decisionHandler(.cancel, contentMode)
            } else {
                decisionHandler(.allow, contentMode)
            }
        }  else {
            
            if #available(iOS 14.5, *) {
                if navigationAction.shouldPerformDownload || urlString.contains("trustedx-resources/esignsp/v2/ui/documents/"){
                    //                    print("download");
                    decisionHandler(.allow, contentMode)
                } else {
                    print("downloadxxxxx allow");
                    decisionHandler(.allow, contentMode)
                }
            } else {
                print("allow");
                // Fallback on earlier versions
                decisionHandler(.allow, contentMode)
            }
        }
        
    }
    
    public func webView(_ webView: WKWebView, decidePolicyFor navigationResponse: WKNavigationResponse, decisionHandler: @escaping (WKNavigationResponsePolicy) -> Void) {
        
        if navigationResponse.canShowMIMEType {
            decisionHandler(.allow)
        } else {
            if #available(iOS 14.5, *) {
                decisionHandler(.allow)
            } else {
                // Fallback on earlier versions
                decisionHandler(.allow)
            }
        }
    }
    
    public func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration, for navigationAction: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
        
        // open in current view
        let viewController = PDFWebViewController()
        
        // the url can be a web url or a file url
        configuration.websiteDataStore.httpCookieStore.getAllCookies({ cookies in
            viewController.cookies = cookies;
            viewController.pdfURL = navigationAction.request.url!
            self.present(viewController, animated: true);
        })
        //            webView.load(navigationAction.request)
        
        // don't return a new view to build a popup into (the default behavior).
        return nil;
    }
    
    public func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        
        if error._code == -1001 || error._code == -1003 || error._code == -1100 {
            if error._code == -1001 { // TIMED OUT:
                // CODE to handle TIMEOUT
                print("CODE to handle TIMEOUT")
            } else if error._code == -1003 { // SERVER CANNOT BE FOUND
                // CODE to handle SERVER not found
                print("CODE to handle SERVER not found")
            } else if error._code == -1100 { // URL NOT FOUND ON SERVER
                // CODE to handle URL not found
                print("CODE to handle URL not found")
            }
            skipDismiss = true
            alreadyCanceled = true
            onUAEPassFailureBlock?("cancel")
        }
    }
    
    func storeCookies() {
        let cookiesStorage = HTTPCookieStorage.shared
        let userDefaults = UserDefaults.standard
        
        
        webView!.configuration.websiteDataStore.httpCookieStore.getAllCookies({ cookies in
            var cookieDict = [String : AnyObject]()
            for cookie in cookies {
                cookieDict[cookie.name] = cookie.properties as AnyObject?
            }
            userDefaults.set(cookieDict, forKey: "cookiesKey")
            userDefaults.synchronize()
        })
    }
    
    func restoreCookies() {
        let cookiesStorage = webView!.configuration.websiteDataStore.httpCookieStore
        let userDefaults = UserDefaults.standard
        
        if let cookieDictionary = userDefaults.dictionary(forKey: "cookiesKey") {
            
            for (_, cookieProperties) in cookieDictionary {
                if let cookie = HTTPCookie(properties: cookieProperties as! [HTTPCookiePropertyKey : Any] ) {
                    cookiesStorage.setCookie(cookie)
                }
            }
        }
    }
}


// MARK: - ConfigrationInstanceProtocol
@available(iOS 13.0, *)
extension UAEPassWebViewController: ConfigrationInstanceProtocol {
    @objc public static func instantiate() -> NSObject {
        let bundle = Bundle.init(for: UAEPassWebViewController.self)
        let object = UAEPassWebViewController(nibName: Identifier, bundle: bundle)
        return object
    }
}


public extension UIView {
    typealias ConstraintsTupleStretched = (top:NSLayoutConstraint, bottom:NSLayoutConstraint, leading:NSLayoutConstraint, trailing:NSLayoutConstraint)
    func addSubviewStretched(subview:UIView?, insets: UIEdgeInsets = UIEdgeInsets() ) -> ConstraintsTupleStretched? {
        guard let subview = subview else {
            return nil
        }
        
        subview.translatesAutoresizingMaskIntoConstraints = false
        addSubview(subview)
        
        let constraintLeading = NSLayoutConstraint(item: subview,
                                                   attribute: .left,
                                                   relatedBy: .equal,
                                                   toItem: self,
                                                   attribute: .left,
                                                   multiplier: 1,
                                                   constant: insets.left)
        addConstraint(constraintLeading)
        
        let constraintTrailing = NSLayoutConstraint(item: self,
                                                    attribute: .right,
                                                    relatedBy: .equal,
                                                    toItem: subview,
                                                    attribute: .right,
                                                    multiplier: 1,
                                                    constant: insets.right)
        addConstraint(constraintTrailing)
        
        let constraintTop = NSLayoutConstraint(item: subview,
                                               attribute: .top,
                                               relatedBy: .equal,
                                               toItem: self,
                                               attribute: .top,
                                               multiplier: 1,
                                               constant: insets.top)
        addConstraint(constraintTop)
        
        let constraintBottom = NSLayoutConstraint(item: self,
                                                  attribute: .bottom,
                                                  relatedBy: .equal,
                                                  toItem: subview,
                                                  attribute: .bottom,
                                                  multiplier: 1,
                                                  constant: insets.bottom)
        addConstraint(constraintBottom)
        return (constraintTop, constraintBottom, constraintLeading, constraintTrailing)
    }
    
}
