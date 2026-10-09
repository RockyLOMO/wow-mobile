-- WoW Mobile Touch UI v8 (Old Dream, native mode / loaded addon cleanup)
local frame = CreateFrame("Frame")
local dirty, applying = true, false
frame:RegisterEvent("PLAYER_LOGIN")
frame:RegisterEvent("PLAYER_ENTERING_WORLD")
frame:RegisterEvent("DISPLAY_SIZE_CHANGED")
frame:RegisterEvent("UI_SCALE_CHANGED")
frame:RegisterEvent("PLAYER_REGEN_ENABLED")
local function updateLayout()
    if InCombatLockdown() then return end
    applying = true
    -- Leave UIParent under the game's control so panels and bag anchors agree.
    -- Fit the entire bottom bar, including its backpack buttons, inside the screen.
    if MainMenuBar and not InCombatLockdown() then
        local available = UIParent:GetWidth() - 48
        MainMenuBar:SetScale(math.min(1.35, available / MainMenuBar:GetWidth()))
    end
    -- Twelve enlarged buttons fit beside the play area as two columns of six.
    -- Keep the original secure buttons, action slots and visibility rules.
    for group, name in ipairs(WoWMobileNativeTouch ~= false and {"MultiBarRight", "MultiBarLeft"} or {}) do
        local bar = _G[name]
        local first = _G[name .. "Button1"]
        if bar and first then
            local width, height = first:GetWidth(), first:GetHeight()
            local gap = 6
            bar:SetScale(1.30)
            -- WoW 3.3.5 renders its 768-high UI canvas into the configured viewport.
            local renderHeight = tonumber((GetCVar("gxResolution") or ""):match("x(%d+)")) or 720
            local canvasScale = renderHeight / (UIParent:GetHeight() * UIParent:GetEffectiveScale())
            local scale = bar:GetEffectiveScale() * canvasScale
            local blockWidth = (2 * width + gap) * scale
            bar:ClearAllPoints()
            bar:SetPoint("TOPRIGHT", UIParent, "TOPRIGHT",
                -(4 + (group - 1) * (blockWidth + 10)) / scale, -225 / scale)
            bar:SetWidth(2 * width + gap)
            bar:SetHeight(6 * height + 5 * gap)
            for i = 1, 12 do
                local button = _G[name .. "Button" .. i]
                if button then
                    button:ClearAllPoints()
                    button:SetPoint("TOPRIGHT", bar, "TOPRIGHT",
                        -math.floor((i - 1) / 6) * (width + gap), -((i - 1) % 6) * (height + gap))
                    -- Fill half the inter-button gap on each side without overlapping neighbours.
                    button:SetHitRectInsets(-gap / 2, -gap / 2, -gap / 2, -gap / 2)
                end
            end
        end
    end
    -- Enlarge text objects without changing panel geometry or UIParent's scale.
    for _, entry in ipairs({
        {"GameFontNormal", 13}, {"GameFontHighlight", 13}, {"GameFontDisable", 13},
        {"GameTooltipText", 14}, {"GameTooltipHeaderText", 16}, {"GameTooltipTextSmall", 12}
    }) do
        local font = _G[entry[1]]
        if font then
            local path, size, flags = font:GetFont()
            if path and size and size < entry[2] then font:SetFont(path, entry[2], flags) end
        end
    end
    if FCF_SetChatWindowFontSize then
        for i = 1, 2 do
            local chatFrame = _G["ChatFrame" .. i]
            if chatFrame then
                local _, size = chatFrame:GetFont()
                if size and size < 16 then
                    FCF_SetChatWindowFontSize(nil, chatFrame, 16)
                end
            end
        end
    end
    applying = false
    dirty = false
end
frame:SetScript("OnEvent", function(_, event)
    if event == "PLAYER_LOGIN" and WoWMobileNativeTouch ~= false then
        -- A missed skill tap landing on the ground must not clear the current target.
        SetCVar("deselectOnClick", "0")
        local reload = false
        for i = 1, GetNumAddOns() do
            local name = GetAddOnInfo(i)
            if name and name:match("^ConsolePort") then
                -- Enabled controls the next load; disabled addons can still be running.
                if IsAddOnLoaded(name) then reload = true end
                DisableAddOn(name)
            end
        end
        if reload then ReloadUI(); return end
        for _, binding in ipairs({{"W", "MOVEFORWARD"}, {"S", "MOVEBACKWARD"},
            {"A", "STRAFELEFT"}, {"D", "STRAFERIGHT"}, {"SPACE", "JUMP"}, {"TAB", "TARGETNEARESTENEMY"}}) do
            SetBinding(binding[1], binding[2])
        end
        SaveBindings(GetCurrentBindingSet())
    end
    dirty = true
end)
frame:SetScript("OnUpdate", function()
    if dirty and not InCombatLockdown() then updateLayout() end
end)
if UIParent_ManageFramePositions then
    hooksecurefunc("UIParent_ManageFramePositions", function()
        if not applying then dirty = true end
    end)
end
